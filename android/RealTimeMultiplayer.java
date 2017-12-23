package org.godotengine.godot;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import org.godotengine.godot.GodotLib;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;

import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Invitations;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnCompleteListener;

public class RealTimeMultiplayer implements RoomUpdateListener, RealTimeMessageReceivedListener, RoomStatusUpdateListener, OnInvitationReceivedListener {
    public final static int RC_SELECT_PLAYERS = 9006;
    public final static int RC_INVITATION_INBOX = 9008;
    public final static int RC_WAITING_ROOM = 9007;

    private static RealTimeMultiplayer m_instance = null;
    private Activity m_activity = null;
    private static int m_scriptInstanceId;

    private static final String TAG = "RealTimeMultiplayer";

    /* Room ID where the currently active game is taking place; null if we are not playing */
    private Room m_room = null;
    private RoomConfig m_joinedRoomConfig = null;

    private HashSet<Integer> m_pendingMessageSet = new HashSet<>();


    /* The participants in the currently active game */
    ArrayList<Participant> participants = null;

    /* My participant ID in the currently active game */
    String myId = null;

    /* Id for invitation */
    String incomingInvitationId = null;

    private RealTimeMultiplayerClient.ReliableMessageSentCallback handleMessageSentCallback =
        new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
            @Override
            public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientId) {
                // handle the message being sent.
                synchronized (this) {
                    m_pendingMessageSet.remove(tokenId);
                }
            }
        };

    public static RealTimeMultiplayer getInstance (Activity p_activity) {
		if (m_instance == null) {
			synchronized (RealTimeMultiplayer.class) {
				m_instance = new RealTimeMultiplayer(p_activity);
			}
		}

		return m_instance;
	}

    public RealTimeMultiplayer(Activity activity) {
        m_activity = activity;
    }

    public void init(final int InstanceId) {
        m_scriptInstanceId = InstanceId;
    }

    /**
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

    public void onActivityResult(int request, int response, Intent intent) {
        switch(request) {
            case RealTimeMultiplayer.RC_SELECT_PLAYERS:
                if(response != Activity.RESULT_OK)
                    return;

                Bundle extras = intent.getExtras();
                final ArrayList<String> invites = intent.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

                Bundle autoMatchCriteria = null;
                int minAutoMatchPalyers = intent.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
                int maxAutoMatchPlayers = intent.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

                if(minAutoMatchPalyers > 0)
                    autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPalyers, maxAutoMatchPlayers, 0);
                else
                    autoMatchCriteria = null;

                RoomConfig.Builder roomConfigBuilder = makeBasicRoomConfigBuilder();
                roomConfigBuilder.addPlayersToInvite(invites);
                if(autoMatchCriteria != null)
                    roomConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);
                RoomConfig roomConfig = roomConfigBuilder.build();
                Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity)).create(roomConfig);
                m_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                handleInvitationInboxResult(response, intent);
                break;
        }
    }

    private void handleInvitationInboxResult(int response, Intent data) {
        if (response != Activity.RESULT_OK) {
            Log.w(TAG, "Invitation inbox UI canceled: " + response);
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_invitation_canceled", new Object[] {});
            return;
        }

        Log.d(TAG, "Invitation inbox UI succeeded");
        Invitation inv = data.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);

        acceptInviteToRoom(inv.getInvitationId());
    }

    private void acceptInviteToRoom(String invitationId) {
        Log.d(TAG, "Accepting invitation: " + invitationId);
        RoomConfig.Builder roomConfigBuilder = RoomConfig.builder(this)
            .setInvitationIdToAccept(invitationId)
            .setMessageReceivedListener(this)
            .setRoomStatusUpdateListener(this);
        m_joinedRoomConfig = roomConfigBuilder.build();

        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .join(m_joinedRoomConfig);

        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_invitation_accepted", new Object[] {});
        m_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private RoomConfig.Builder makeBasicRoomConfigBuilder() {
        return RoomConfig.builder(this)
        .setMessageReceivedListener(this)
        .setRoomStatusUpdateListener(this);
    }

    @Override
    public void onInvitationReceived(Invitation invitation) {
        incomingInvitationId = invitation.getInvitationId();
        Log.d(TAG, "Invitation received: " + incomingInvitationId);
        //TODO: Send invitation info
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_invitation_received", new Object[] { incomingInvitationId });
    }

    @Override
    public void onInvitationRemoved(String invitationId) {
        if(incomingInvitationId.equals(invitationId) && incomingInvitationId != null) {
            Log.d(TAG, "Invitation removed: " + invitationId);
            incomingInvitationId = null;
            GodotLib.calldeferred(m_scriptInstanceId, "_rtm_invitation_removed", new Object[] { invitationId });
        }
    }

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

    @Override
    public void onRoomConnected(int statusCode, Room room) {
        Log.d(TAG, "onRoomConnected(" + statusCode + ", " + room + ")");
        if(statusCode != GamesStatusCodes.STATUS_OK) {
            Log.e(TAG, "Error: onRoomConnected, status " + statusCode);
            showGameError();
            return;
        }
        updateRoom(room);
    }

    @Override
    public void onRoomCreated(int statusCode, Room room) {
        if(statusCode != GamesCallbackStatusCodes.OK) {
            Log.e(TAG, "Error: onRoomCreated, status " + statusCode);
            m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            showGameError();
            return;
        }

        Log.d(TAG, "Room " + room.getRoomId() + " created.");
        m_room = room;
    }

    @Override
    public void onJoinedRoom(int statusCode, Room room) {
        Log.d(TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");

        if(statusCode != GamesStatusCodes.STATUS_OK) {
            Log.e(TAG, "Error: onJoinedRoom, status " + statusCode);
            m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            showGameError();
            return;
        }

        
        showWaitingRoom(m_room, m_room.getParticipantIds().size());
    }

    @Override
    public void onLeftRoom(int statusCode, String roomId) {
        // we have left the room; return to main screen.
        Log.d(TAG, "onLeftRoom, code " + statusCode);
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_left_room", new Object[] { });
    }

    @Override
    public void onDisconnectedFromRoom(Room room) {
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .leave(m_joinedRoomConfig, m_room.getRoomId());
        m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        m_room = null;
        m_joinedRoomConfig = null;
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_disconnected", new Object[] { });
    }

    @Override
    public void onConnectedToRoom(Room room) {
        Log.d(TAG, "onConnectedToRoom.");

        m_room = room;

        Games.getPlayersClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .getCurrentPlayerId()
            .addOnSuccessListener(new OnSuccessListener<String>() {
                @Override
                public void onSuccess(String playerId) {
                    myId = m_room.getParticipantId(playerId);
                    Log.d(TAG, "Room ID: " + m_room.getRoomId());
                    Log.d(TAG, "My ID: " + myId);
                    Log.d(TAG, "Connected to room!");
                }
            });        
    }

    void showGameError() {
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_show_game_error", new Object[] {});
    }

    @Override
    public void onRealTimeMessageReceived(RealTimeMessage rtm) {
        byte[] buf = rtm.getMessageData();
        String sender = rtm.getSenderParticipantId();
        Log.d(TAG, "Message received: " + (char)buf[0] + "/" + (int) buf[1]);
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_message_received", new Object[] { buf });
    }

    void updateRoom(Room room) {
        if (room != null)
            participants = room.getParticipants();
        if(participants != null){
            //Nothing
        }
    }

    void showWaitingRoom(Room room, int maxPlayersToStart) {
        final int MIN_PLAYERS = Integer.MAX_VALUE;
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .getWaitingRoomIntent(m_room, maxPlayersToStart)
            .addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) { 
                    m_activity.startActivityForResult(intent, RC_WAITING_ROOM);
                }
            });
    }

    public void leaveRoom() {
        Log.d(TAG, "Leaving room.");
        if (m_room != null) {
            Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
                .leave(m_joinedRoomConfig, m_room.getRoomId());
            m_room = null;
        }
        m_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        GodotLib.calldeferred(m_scriptInstanceId, "_rtm_on_leaved_room", new Object[] {});
        Log.d(TAG, "Leaving room.");
    }

    @Override
    public void onPeerDeclined(Room room, List<String> arg) {
        updateRoom(room);
    }

    @Override
    public void onPeerInvitedToRoom(Room room, List<String> arg) {
        updateRoom(room);
    }

    @Override
    public void onP2PDisconnected(String participant) {
    }

    @Override
    public void onP2PConnected(String participant) {
    }

    @Override
    public void onPeerJoined(Room room, List<String> arg1) {
        updateRoom(room);
    }

    @Override
    public void onPeerLeft(Room room, List<String> peersWhoLeft) {
        updateRoom(room);
    }

    @Override
    public void onRoomAutoMatching(Room room) {
        updateRoom(room);
    }

    @Override
    public void onRoomConnecting(Room room) {
        updateRoom(room);
    }

    @Override
    public void onPeersConnected(Room room, List<String> peers) {
        updateRoom(room);
    }

    @Override
    public void onPeersDisconnected(Room room, List<String> peers) {
        updateRoom(room);
    }

    synchronized void recordMessageToken(int tokenId) {
        m_pendingMessageSet.add(tokenId);
    }    

    public void sendReliableMessage(String msg, String participantId) {
        byte[] message = msg.getBytes();
        Games.getRealTimeMultiplayerClient(m_activity, GoogleSignIn.getLastSignedInAccount(m_activity))
            .sendReliableMessage(message, m_room.getRoomId(), participantId, handleMessageSentCallback)
            .addOnCompleteListener(new OnCompleteListener<Integer>(){
                @Override
                public void onComplete(Task<Integer> task) {
                    // Keep track of which messages are sent, if desired.
                    recordMessageToken(task.getResult());
                }

            });
    }

    public void sendBroadcastMessage(String msg) {
        for(Participant p: participants) {
            String participantId = p.getParticipantId();
            if(!participantId.equals(myId)) {
                sendReliableMessage(msg, participantId);
            }
        }
    }
}