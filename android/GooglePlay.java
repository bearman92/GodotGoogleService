package org.godotengine.godot;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import org.godotengine.godot.RealTimeMultiplayer;

public class GooglePlay extends Godot.SingletonBase {
	private static Activity m_activity;

	static public Godot.SingletonBase initialize (Activity activity) {
		return new GooglePlay(activity);
	}

	public GooglePlay(Activity activity) {
		registerClass ("GooglePlay", new String[] {
			//Base methods
			"init", 
			"login", 
			"logout", 
			"is_connected", 
			"get_version_code",
			//Achievements
			"unlock_achievement", 
			"increse_achievement", 
			"show_achievements",
			//Leaderboard
			"submit_leaderboard", 
			"show_leaderboard", 
			"show_leaderboards", 
			//Realtime Multiplayer
			"invite_players", 
			"start_quick_game",
			"show_invitation_inbox", 
			"show_waiting_room",
			"send_reliable_message", 
			"broadcast_reliable_message",
			"send_unreliable_message",
			"broadcast_unreliable_message",
			"leave_room",
			"get_participant_name"
		});

		m_activity = activity;
	}

	public int get_version_code(final int instanceID) {
		try {
			final PackageInfo pInfo = m_activity.getPackageManager().getPackageInfo(m_activity.getPackageName(), 0);

			return pInfo.versionCode;
		} catch (NameNotFoundException e) { }

		return 0;
	}

	public void init(final int instanceID) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).init(instanceID);
				RealTimeMultiplayer.getInstance(m_activity).init(instanceID);
			}
		});
	}

	public void login() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).signIn();
			}
		});
	}

	public void logout() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).disconnect();
			}
		});
	}

	public boolean is_connected() {
		return PlayService.getInstance(m_activity).isConnected();
	}

	public void unlock_achievement(final String id) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).achievementUnlock(id);
			}
		});
	}

	public void increse_achievement(final String id, final int steps) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).achievementIncrement(id, steps);
			}
		});
	}

	public void show_achievements() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).achievementShowList();
			}
		});
	}

	public void submit_leaderboard(final int score, final String l_id) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).leaderboardSubmit(l_id, score);
			}
		});
	}

	public void show_leaderboard(final String l_id) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).leaderboardShow(l_id);
			}
		});
	}

	public void show_leaderboards() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PlayService.getInstance(m_activity).leaderboardShowList();
			}
		});
	}

	public void invite_players(final int minimumPlayersToInvite, final int maximumPlayersToInvite) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).invitePlayers(minimumPlayersToInvite - 1, maximumPlayersToInvite - 1);
			}
		});
	}

	public void start_quick_game(final int minimumPlayersToInvite, final int maximumPlayersToInvite, final int role)
	{
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).startQuickGame(minimumPlayersToInvite, maximumPlayersToInvite, role);
			}
		});
	}

	public void show_invitation_inbox() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).showInvitationInbox();
			}
		});
	}

	public void show_waiting_room() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).showWaitingRoom();
			}
		});
	}

	public void send_reliable_message(final String msg, final String participant_id) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				byte[] bytes = msg.getBytes();
				RealTimeMultiplayer.getInstance(m_activity).sendReliableMessage(bytes, participant_id);
			}
		});
	}

	public void broadcast_reliable_message(final String msg) {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				byte[] bytes = msg.getBytes();
				RealTimeMultiplayer.getInstance(m_activity).broadcastReliableMessage(bytes);
			}
		});
	}

	public void send_unreliable_message(final String msg, final String participant_id) {
		m_activity.runOnUiThread(new Runnable() {
			 @Override
			 public void run() {
			 	byte[] bytes = msg.getBytes();
				 RealTimeMultiplayer.getInstance(m_activity).sendUnreliableMessage(bytes, participant_id);
			 }
		 });
	}

	public void leave_room() {
		m_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).leaveRoom();
			}
		});
	}

	public String get_participant_name(final String participantId) {
		return RealTimeMultiplayer.getInstance(m_activity).getParticipantName(participantId);
	}

	protected void onMainActivityResult (int requestCode, int resultCode, Intent data) {
		PlayService.getInstance(m_activity).onActivityResult(requestCode, resultCode, data);
		RealTimeMultiplayer.getInstance(m_activity).onActivityResult(requestCode, resultCode, data);
	}

	protected void onMainPause () {
		PlayService.getInstance(m_activity).onPause();
	}

	protected void onMainResume () {
//		mFirebaseAnalytics.setCurrentScreen(activity, "Main", currentScreen);
		PlayService.getInstance(m_activity).onResume();
	}

	protected void onMainDestroy () {
		PlayService.getInstance(m_activity).onStop();
	}
}
