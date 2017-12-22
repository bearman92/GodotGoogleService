
package org.godotengine.godot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.util.Log;
import android.view.View;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONException;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.Task;

public class PlayService {

	private static Activity m_activity = null;
	private static PlayService m_instance = null;

	private static int m_scriptId;

	private static final int GOOGLE_SIGN_IN_REQUEST	= 9001;
	private static final int REQUEST_ACHIEVEMENTS = 9002;
	private static final int REQUEST_LEADERBOARD = 9003;
	private static final String TAG = "GoogleService";

	private Boolean m_isIntentInProgress = false;
	private Boolean m_isResolvingConnectionFailure = false;

	private GoogleSignInClient m_googleSignInClient;
	private GoogleSignInAccount m_account;
	private AchievementsClient m_achievementsClient;
	private LeaderboardsClient m_leaderboardsClient;
	private PlayersClient m_playersClient;

	public static PlayService getInstance (Activity p_activity) {
		if (m_instance == null) {
			synchronized (PlayService.class) {
				m_instance = new PlayService(p_activity);
			}
		}

		return m_instance;
	}

	public PlayService(Activity p_activity) {
		m_activity = p_activity;
	}

	public void init(final int instanceID) {
		m_scriptId = instanceID;
		GUtils.setScriptInstance(m_scriptId);

		if (GUtils.checkGooglePlayService(m_activity)) {
			Log.d(TAG, "Play Service Available.");
		}

		GoogleSignInOptions gso =
		new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
		.requestScopes(new Scope(Scopes.GAMES))
		.requestEmail()
		.build();

		m_googleSignInClient = GoogleSignIn.getClient(m_activity, gso);

		Log.d(TAG, "Google::Initialized");
		onStart();
	}

	public boolean isConnected() {
		m_account = GoogleSignIn.getLastSignedInAccount(m_activity);
		return m_account != null;
	}

	public void connect() {
		if (m_googleSignInClient == null) {
			Log.d(TAG, "GoogleSignInClient not initialized");
			return;
		}

		if (isConnected()) {
			Log.d(TAG, "Google service is already connected");
			return;
		}

		Intent signInIntent = m_googleSignInClient.getSignInIntent();
		m_activity.startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST);
	}

	public void disconnect() {
		m_googleSignInClient.signOut()
		.addOnCompleteListener(m_activity, new OnCompleteListener<Void>() {
			@Override
			public void onComplete(@NonNull Task<Void> task) {
				Log.d(TAG, "Google signed out.");

				m_achievementsClient = null;
				m_leaderboardsClient = null;
				m_playersClient = null;

				GUtils.callScriptFunc("login", "false");
			}
		});
	}

	public void succeedSignIn() {
		Log.d(TAG, "Google signed in.");

		m_achievementsClient = Games.getAchievementsClient(m_activity, m_account);
		m_leaderboardsClient = Games.getLeaderboardsClient(m_activity, m_account);
		m_playersClient = Games.getPlayersClient(m_activity, m_account);

		Games.getGamesClient(m_activity, m_account).setViewForPopups(m_activity.getWindow().getDecorView().findViewById(android.R.id.content));

		GUtils.callScriptFunc("login", "true");

		m_playersClient.getCurrentPlayer()
		.addOnCompleteListener(new OnCompleteListener<Player>() {
			@Override
			public void onComplete(@NonNull Task<Player> task) {
				String displayName = "UserName";

				if (task.isSuccessful()) {
					displayName = task.getResult().getDisplayName();
				} else {
					Exception e = task.getException();
				}

				GUtils.callScriptFunc("user", displayName);
                    }
		});
	}

	public void achievementUnlock(final String achievement_id) {
		connect();

		if (isConnected()) {
			// KeyValueStorage.setValue(achievement_id, "true");
			m_achievementsClient.unlock(achievement_id);

			Log.i(TAG, "PlayGameServices: achievement_unlock");
		} else { Log.w(TAG, "PlayGameServices: Google calling connect"); }
	}

	public void achievementIncrement(final String achievement_id, final int amount) {
		connect();

		if (isConnected()) {
			m_achievementsClient.increment(achievement_id, amount);

			Log.i(TAG, "PlayGameServices: achievement_incresed");
		} else { Log.i(TAG, "PlayGameServices: Google calling connect"); }
	}

	public void achievementShowList() {
		connect();

		if (isConnected()) {
			m_achievementsClient.getAchievementsIntent()
			.addOnSuccessListener(new OnSuccessListener<Intent>() {
				@Override
				public void onSuccess(Intent intent) {
					m_activity.startActivityForResult(intent, REQUEST_ACHIEVEMENTS);
				}
			})
			.addOnFailureListener(new OnFailureListener() {
				@Override
				public void onFailure(@NonNull Exception e) {
					Log.d(TAG, "Showing::Loaderboard::Failed:: " + e.toString());
				}
			});

		} else { Log.i(TAG, "PlayGameServices: Google calling connect"); }
	}

	public void leaderboardSubmit(String id, int score) {
		connect();

		if (isConnected()) {
			m_leaderboardsClient.submitScore(id, score);

			Log.i(TAG, "PlayGameServices: leaderboardSubmit, " + score);
		} else { Log.i(TAG, "PlayGameServices: Google calling connect"); }
	}

	public void leaderboardShow(final String l_id) {
		connect();

		if (isConnected()) {
			m_leaderboardsClient.getLeaderboardIntent(l_id)
			.addOnSuccessListener(new OnSuccessListener<Intent>() {
				@Override
				public void onSuccess (Intent intent) {
					Log.d(TAG, "Showing::Loaderboard::" + l_id);
					m_activity.startActivityForResult(intent, REQUEST_LEADERBOARD);
				}
			})
			.addOnFailureListener(new OnFailureListener() {
				@Override
				public void onFailure(@NonNull Exception e) {
					Log.d(TAG, "Showing::Loaderboard::Failed:: " + e.toString());
				}
			});

		} else { Log.i(TAG, "PlayGameServices: Google not connected calling connect"); }
	}

	public void leaderboardShowList() {
		connect();

		if (isConnected()) {
			m_leaderboardsClient.getAllLeaderboardsIntent()
			.addOnSuccessListener(new OnSuccessListener<Intent>() {
				@Override
				public void onSuccess (Intent intent) {
					Log.d(TAG, "Showing::Loaderboard::List");
					m_activity.startActivityForResult(intent, REQUEST_LEADERBOARD);
				}
			})
			.addOnFailureListener(new OnFailureListener() {
				@Override
				public void onFailure(@NonNull Exception e) {
					Log.d(TAG, "Showing::Loaderboard::Failed:: " + e.toString());
				}
			});

		} else { Log.i(TAG, "PlayGameServices: Google not connected calling connect"); }
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
			m_isIntentInProgress = false;

			GoogleSignInResult result =
			Auth.GoogleSignInApi.getSignInResultFromIntent(data);

			handleSignInResult(result);
		}
	}

	private void handleSignInResult(GoogleSignInResult result) {
		if (result.isSuccess()) {
			m_account = result.getSignInAccount();
			succeedSignIn();
		} else {
			Status s = result.getStatus();

			GUtils.callScriptFunc("login_error", String.valueOf(s.getStatusCode()));

			Log.w(TAG, "SignInResult::Failed code="
			+ s.getStatusCode() + ", Message: " + s.getStatusMessage());

			if (m_isResolvingConnectionFailure) { return; }
			if (!m_isIntentInProgress && result.getStatus().hasResolution()) {
				try {
					m_isIntentInProgress = true;

					m_activity.startIntentSenderForResult(
					s.getResolution().getIntentSender(),
					GOOGLE_SIGN_IN_REQUEST, null, 0, 0, 0);
				} catch (SendIntentException ex) {
					connect();
				}

				m_isResolvingConnectionFailure = true;
			}
		}
	}

	private void signInSilently() {
		if (isConnected()) { return; }

		GoogleSignInClient signInClient = GoogleSignIn.getClient(m_activity,
		GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);

		signInClient.silentSignIn().addOnCompleteListener(m_activity,
		new OnCompleteListener<GoogleSignInAccount>() {
			@Override
			public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
				if (task.isSuccessful()) {
					// The signed in account is stored in the task's result.
					try {
						m_account = task.getResult(ApiException.class);
						succeedSignIn();
					} catch (ApiException e) {
						Log.w(TAG, "SignInResult::Failed code="
						+ e.getStatusCode() + ", Message: "
						+ e.getStatusMessage());
					}
				} else {
					// Player will need to sign-in explicitly using via UI
					Log.d(TAG, "Silent::Login::Failed");
				}
			}
		});
	}

	public void onStart() {
		m_account = GoogleSignIn.getLastSignedInAccount(m_activity);

		if (m_account != null) {
			Log.d(TAG, "Google already connected to an account");
			succeedSignIn();
		} else {
			Log.d(TAG, "Google not connected");
			connect();
			//signInSilently();
		}

		Boolean autoLaunchDeepLink = true;
		/**
		// Check for App Invite invitations and launch deep-link activity if possible.
		// Requires that an Activity is registered in AndroidManifest.xml to handle
		// deep-link URLs.

		FirebaseDynamicLinks.getInstance().getDynamicLink(getIntent())
		.addOnSuccessListener(this, new OnSuccessListener<PendingDynamicLinkData>() {
                @Override
                public void onSuccess(PendingDynamicLinkData data) {
                    if (data == null) {
                        Log.d(TAG, "getInvitation: no data");
                        return;
                    }

                    // Get the deep link
                    Uri deepLink = data.getLink();

                    // Extract invite
                    FirebaseAppInvite invite = FirebaseAppInvite.getInvitation(data);
                    if (invite != null) {
                        String invitationId = invite.getInvitationId();
                    }

                    // Handle the deep link
                    // ...
                }
		}).addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.w(TAG, "getDynamicLink:onFailure", e);
                }
		});
		**/
	}

	public void onPause() {

	}

	public void onResume() {
		// Hide Google play UI's
		//signInSilently();
	}

	public void onStop() {
		m_activity = null;
	}
}
