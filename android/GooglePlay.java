package org.godotengine.godot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.IntentSender.SendIntentException;
import android.util.Log;
import android.view.View;
import android.os.Bundle;


import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONException;

public class GooglePlay extends Godot.SingletonBase {
	private static Activity m_activity;

	static public Godot.SingletonBase initialize (Activity activity) {
		return new GooglePlay(activity);
	}

	public GooglePlay(Activity activity) {
		registerClass ("GooglePlay", new String[] {
			"init", "login", "logout", "is_connected", "get_version_code", //Base methods
			"unlock_achievement", "increse_achievement", "show_achievements", //Achievements
			"submit_leaderboard", "show_leaderboard", "show_leaderboards", //Leaderboard
			"invite_players", "show_invitation_inbox", "send_reliable_message", "send_broadcast_message", "leave_room" //Realtime Multiplayer
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
			public void run() {
				PlayService.getInstance(m_activity).init(instanceID);
				RealTimeMultiplayer.getInstance(m_activity).init(instanceID);
			}
		});
	}

	public void login() {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).connect();
			}
		});
	}

	public void logout() {
		m_activity.runOnUiThread(new Runnable() {
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
			public void run() {
				PlayService.getInstance(m_activity).achievementUnlock(id);
			}
		});
	}

	public void increse_achievement(final String id, final int steps) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).achievementIncrement(id, steps);
			}
		});
	}

	public void show_achievements() {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).achievementShowList();
			}
		});
	}

	public void submit_leaderboard(final int score, final String l_id) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).leaderboardSubmit(l_id, score);
			}
		});
	}

	public void show_leaderboard(final String l_id) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).leaderboardShow(l_id);
			}
		});
	}

	public void show_leaderboards() {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				PlayService.getInstance(m_activity).leaderboardShowList();
			}
		});
	}

	public void invite_players(final int minimumPlayersToInvite, final int maximumPlayersToInvite) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).invitePlayers(minimumPlayersToInvite - 1, maximumPlayersToInvite - 1);
			}
		});
	}

	public void show_invitation_inbox() {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).showInvitationInbox();
			}
		});
	}

	public void send_reliable_message(final String msg, final String participant_id) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).sendReliableMessage(msg, participant_id);
			}
		});
	}

	public void send_broadcast_message(final String msg) {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).sendBroadcastMessage(msg);
			}
		});
	}

	public void leave_room() {
		m_activity.runOnUiThread(new Runnable() {
			public void run() {
				RealTimeMultiplayer.getInstance(m_activity).leaveRoom();
			}
		});
	}

	protected void onMainActivityResult (int requestCode, int resultCode, Intent data) {
		PlayService.getInstance(m_activity).onActivityResult(requestCode, resultCode, data);
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
