package org.godotengine.godot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RealTimeMultiplayer {
    public final static int RC_SELECT_PLAYERS = 9006;
    public final static int RC_INVITATION_INBOX = 9008;
    public final static int RC_WAITING_ROOM = 9007;

    private final static int MIN_PLAYERS = 2;

    /*
    * Globals
    */
    private static RealTimeMultiplayer m_instance = null;
    private Activity m_activity = null;
    private static int m_scriptInstanceId;

    private static final String TAG = "RealTimeMultiplayer";

    /* Room ID where the currently active game is taking place; null if we are not playing */
    private Room m_room = null;
    private RoomConfig m_joinedRoomConfig = null;
    private Boolean m_playing = false;

    /* My participant ID in the currently active game */
    private String m_myParticipantId;

    private HashSet<Integer> m_pendingMessageSet = new HashSet<>();

    /*
    * Message sent callback
    */
    private RealTimeMultiplayerClient.ReliableMessageSentCallback m_handleMessageSentCallback =
        new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
            @Override
            public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientId) {
                // handle the message being sent.
                synchronized (this) {
                    m_pendingMessageSet.remove(tokenId);
                }
            }
        };

    /*
    * Runtime invitations callback
     */
    private InvitationCallback m_invitationCallback = new InvitationCallback() {
		@Override
		public void onInvitationReceived(@NonNull Invitation invitation) {
			//TODO: Handle new invitation
		}

		@Override
		public void onInvitationRemoved(@NonNull String s) {
			//TODO: Delete invitation from queue
		}
	};

    /*
    * Room update callback
    */
    private RoomUpdateCallback m_roomUpdateCallback = new RoomUpdateCallback() {
        @Override
        public void onRoomCreated(int code, Room room) {
            // Update UI and internal state based on room updates
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(TAG, "Room " + room.getRoomId() + " created.");
                m_room = room;
                //showWaitingRoom();
            } else {
                Log.w(TAG, "Error creating room: " + code);
                // let screen go to sleep
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            }
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_room_created", new Object[] { });
        }

        @Override
        public void onJoinedRoom(int code, Room room) {
            // Update UI and internal state based on room updates.
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(TAG, "Room " + room.getRoomId() + " joined.");
                m_room = room;
                showWaitingRoom();
            } else {
                Log.w(TAG, "Error joining room: " + code);
                // let screen go to sleep
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_joined_room", new Object[] { });
        }

        @Override
        public void onLeftRoom(int code, String roomId) {
            Log.d(TAG, "Left room" + roomId);
            m_room = null;
			m_playing = false;

            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_left_room", new Object[] { });
        }

        @Override
        public void onRoomConnected(int code, Room room) {
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(TAG, "Room " + room.getRoomId() + " connected.");
                m_room = room;
            } else {
                Log.w(TAG, "Error connecting to room: " + code);
                // let screen go to sleep
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            }

            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_room_connected", new Object[] { m_room.getParticipantIds() });
        }
    };

    /*
    * Room status update callback
    */
    private RoomStatusUpdateCallback m_roomStatusCallback = new RoomStatusUpdateCallback() {
        @Override
        public void onRoomConnecting(Room room) {
            // Update the UI status since we are in the process of connecting to a specific room.
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_room_connecting", new Object[] { });
        }

        @Override
        public void onRoomAutoMatching(Room room) {
            // Update the UI status since we are in the process of matching other players.
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_automatchmaking", new Object[] { });
        }

        @Override
        public void onPeerInvitedToRoom(Room room, List<String> list) {
            // Update the UI status since we are in the process of matching other players.
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_peer_invited_to_room", new Object[] { });
        }

        @Override
        public void onPeerDeclined(Room room, List<String> list) {
            // Peer declined invitation, see if game should be canceled
            if (!m_playing && shouldCancelGame(room)) {
                Games.getRealTimeMultiplayerClient(m_activity,
                        GoogleSignIn.getLastSignedInAccount(m_activity))
                        .leave(m_joinedRoomConfig, room.getRoomId());
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_peer_declined", new Object[] { });
        }

        @Override
        public void onPeerJoined(Room room, List<String> list) {
            // Update UI status indicating new players have joined!
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_peer_joined", new Object[] { list });
        }

        @Override
        public void onPeerLeft(Room room, List<String> list) {
            // Peer left, see if game should be canceled.
            if (!m_playing && shouldCancelGame(room)) {
                Games.getRealTimeMultiplayerClient(m_activity,
                        GoogleSignIn.getLastSignedInAccount(m_activity))
                        .leave(m_joinedRoomConfig, room.getRoomId());
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            //TODO: Send params
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_peer_left", new Object[] { list });
        }

        @Override
        public void onConnectedToRoom(Room room) {
            // Connected to room, record the room Id.
            m_room = room;
            Games.getPlayersClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
                    .getCurrentPlayerId().addOnSuccessListener(new OnSuccessListener<String>() {
                @Override
                public void onSuccess(String playerId) {
                    m_myParticipantId = m_room.getParticipantId(playerId);
                }
            });
        }

        @Override
        public void onDisconnectedFromRoom(Room room) {
            // This usually happens due to a network error, leave the game.
            Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
                    .leave(m_joinedRoomConfig, room.getRoomId());
            m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // show error message and return to main screen
            m_room = null;
            m_joinedRoomConfig = null;
        }

        @Override
        public void onPeersConnected(Room room, List<String> list) {
            if (m_playing) {
                // add new player to an ongoing game
            } else if (shouldStartGame(room)) {
                // start game!
                m_playing = true;
            }
        }

        @Override
        public void onPeersDisconnected(Room room, List<String> list) {
            if (m_playing) {
                // do game-specific handling of this -- remove player's avatar
                // from the screen, etc. If not enough players are left for
                // the game to go on, end the game and leave the room.
            } else if (shouldCancelGame(room)) {
                // cancel the game
                Games.getRealTimeMultiplayerClient(m_activity,
                        GoogleSignIn.getLastSignedInAccount(m_activity))
                        .leave(m_joinedRoomConfig, room.getRoomId());
                m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        @Override
        public void onP2PConnected(String participantId) {
            // Update status due to new peer to peer connection.
        }

        @Override
        public void onP2PDisconnected(String participantId) {
            // Update status due to  peer to peer connection being disconnected.
        }
    };

    /*
    * RTM message listener
    */
    private OnRealTimeMessageReceivedListener m_messageReceivedHandler =
    new OnRealTimeMessageReceivedListener() {
        @Override
        public void onRealTimeMessageReceived(RealTimeMessage realTimeMessage) {
            // Handle messages received here.
            byte[] message = realTimeMessage.getMessageData();
            String sender = realTimeMessage.getSenderParticipantId();
            // process message contents...
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_message_received", new Object[] { sender, message });
        }
    };

    synchronized void recordMessageToken(int tokenId) {
        m_pendingMessageSet.add(tokenId);
    }

    /*
    * Singleton
    */
    public static RealTimeMultiplayer getInstance (Activity p_activity) {
		if (m_instance == null) {
			synchronized (RealTimeMultiplayer.class) {
				m_instance = new RealTimeMultiplayer(p_activity);
			}
		}

		return m_instance;
	}

    /*
    * Constructor
    */
    public RealTimeMultiplayer(Activity activity) {
        m_activity = activity;
    }

    /*
    * Initialization
    */
    public void init(final int InstanceId) {
        m_scriptInstanceId = InstanceId;
    }

    /*
    * Activity results handler
    */
    public void onActivityResult(int request, int response, Intent intent) {
        switch(request) {
            case RealTimeMultiplayer.RC_SELECT_PLAYERS:
                if(response != Activity.RESULT_OK) {
                    Log.e(TAG, "Wrong response");
                    return;
                }

                Bundle extras = intent.getExtras();
                final ArrayList<String> invites = intent.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

                Bundle autoMatchCriteria = null;
                int minAutoMatchPalyers = intent.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
                int maxAutoMatchPlayers = intent.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

                RoomConfig.Builder roomBuilder = RoomConfig.builder(m_roomUpdateCallback)
                    .setOnMessageReceivedListener(m_messageReceivedHandler)
                    .setRoomStatusUpdateCallback(m_roomStatusCallback)
                    .addPlayersToInvite(invites);

                if(minAutoMatchPalyers > 0)
                {
                    roomBuilder.setAutoMatchCriteria(RoomConfig.createAutoMatchCriteria(minAutoMatchPalyers, maxAutoMatchPlayers, 0));
                }

                m_joinedRoomConfig = roomBuilder.build();
                Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
                    .create(m_joinedRoomConfig);
                break;
            case RealTimeMultiplayer.RC_WAITING_ROOM:
                if(response == Activity.RESULT_OK) {
                    Log.d(TAG, "Starting game (waiting room returned OK).");
                    GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_game_started", new Object[] { });
                } else if (response == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                    leaveRoom();
                } else if (response == Activity.RESULT_CANCELED) {
                    leaveRoom();
                }
                break;
            case RealTimeMultiplayer.RC_INVITATION_INBOX:
                if(response != Activity.RESULT_OK) {
                    Log.e(TAG, "Wrong response");
                    return;
                }
                Invitation invitation = intent.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);
                if(invitation != null) {
                    RoomConfig.Builder builder = RoomConfig.builder(m_roomUpdateCallback)
                        .setInvitationIdToAccept(invitation.getInvitationId())
						.setOnMessageReceivedListener(m_messageReceivedHandler)
                        .setRoomStatusUpdateCallback(m_roomStatusCallback);
                    m_joinedRoomConfig = builder.build();
                    Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
                        .join(m_joinedRoomConfig);
                    m_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                break;
            default:
                Log.e(TAG, "Result responded " + request + " response " + response);
                break;
        }
    }

    /*
     * Invite players with Intent
     */
    public void invitePlayers(final int minimumPlayersToInvite, final int maximumPlayersToInvite) {
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
        .getSelectOpponentsIntent(minimumPlayersToInvite, maximumPlayersToInvite, true)
        .addOnSuccessListener(new OnSuccessListener<Intent>() {
            @Override
            public void onSuccess(Intent intent) {
                m_activity.startActivityForResult(intent, RC_SELECT_PLAYERS);
                Log.d(TAG, "Inviting players (" + minimumPlayersToInvite + ", " + maximumPlayersToInvite + ")");
            }
        });
    }

    /*
    * Auto matchmaking
    */
    public void startQuickGame(int minPlayersToInvite, int maxPlayersToInvite, long role) {
        Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minPlayersToInvite, maxPlayersToInvite, role);

        RoomConfig roomConfig = RoomConfig.builder(m_roomUpdateCallback)
            .setOnMessageReceivedListener(m_messageReceivedHandler)
            .setRoomStatusUpdateCallback(m_roomStatusCallback)
            .setAutoMatchCriteria(autoMatchCriteria)
            .build();

        m_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        m_joinedRoomConfig = roomConfig;
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .create(roomConfig);
    }

    /*
    * Show invites intent
    */
    public void showInvitationInbox() {
        Games.getInvitationsClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .getInvitationInboxIntent()
            .addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) {
                    m_activity.startActivityForResult(intent, RC_INVITATION_INBOX);
                }
            });
    }

    /*
    * Show waiting room intent
     */
    public void showWaitingRoom() {
        final int MAX_PLAYERS = Integer.MAX_VALUE;
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .getWaitingRoomIntent(m_room, MAX_PLAYERS)
            .addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) {
                    m_activity.startActivityForResult(intent, RC_WAITING_ROOM);
                }
            });
    }

    /*
    * Send reliable message to participant
     */
    public void sendReliableMessage(byte[] msg, String participantId) {
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .sendReliableMessage(msg, m_room.getRoomId(), participantId, m_handleMessageSentCallback)
            .addOnCompleteListener(new OnCompleteListener<Integer>() {
                @Override
                public void onComplete(Task<Integer> task) {
                    // Keep track of which messages are sent, if desired.
                    recordMessageToken(task.getResult());
                }
            });
    }

    /*
    * Broadcast reliable message to all participants
     */
    public void broadcastReliableMessage(byte[] msg) {
        for(Participant participant: m_room.getParticipants()) {
            String participantId = participant.getParticipantId();
            if(!participantId.equals(m_myParticipantId)) {
                sendReliableMessage(msg, participantId);
            }
        }
    }

    /*
    * Send unreliable message to participant
     */
    public void sendUnreliableMessage(byte[] msg, String participantId) {
    	Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
				.sendUnreliableMessage(msg, m_room.getRoomId(), participantId);
	}

	/*
	* Broadcast unreliable message to all participants
	 */
	public void broadcastUnreliableMessage(byte[] msg) {
    	Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
				.sendUnreliableMessageToOthers(msg, m_room.getRoomId());
	}

	/*
	* Leave current room
	 */
    public void leaveRoom() {
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .leave(m_joinedRoomConfig, m_room.getRoomId());
        m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public boolean hasAuthority() {
    	if(m_room == null)
    		return false;

    	return m_room.getCreatorId() == m_myParticipantId;
	}

    /*
    * Returns whether the room is in a state where the game should be canceled.
    */
    boolean shouldCancelGame(Room room) {
        // TODO: Your game-specific cancellation logic here. For example, you might decide to
        // cancel the game if enough people have declined the invitation or left the room.
        // You can check a participant's status with Participant.getStatus().
        // (Also, your UI should have a Cancel button that cancels the game too)
        return false;
    }

    /*
    * Returns whether there are enough players to start the game
    */
    boolean shouldStartGame(Room room) {
        int connectedPlayers = 0;
        for (Participant p : room.getParticipants()) {
            if (p.isConnectedToRoom()) {
                ++connectedPlayers;
            }
        }
        return connectedPlayers >= MIN_PLAYERS;
    }

    public String getParticipantName(String participantId) {
    	for(Participant p : m_room.getParticipants()) {
			if(p.getParticipantId().compareTo(participantId) == 0)
				return p.getDisplayName();
		}
		return "";
	}

	public String getParticipantIconURL(String participantId) {
    	for(Participant p : m_room.getParticipants()) {
    		if(p.getParticipantId().compareTo(participantId) == 0)
    			return p.getIconImageUri().toString();
		}
		return "";
	}

	public String getMyParticipantId() {
		return m_myParticipantId;
	}

	public ArrayList<String> getParticipants() {
    	if(m_room == null)
    		return new ArrayList<String>();

    	return m_room.getParticipantIds();
	}

}