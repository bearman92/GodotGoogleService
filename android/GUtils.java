
package org.godotengine.godot;

import android.app.Activity;
import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GUtils {

	public static String readFromFile(String fileName, Context context) {
		StringBuilder returnString = new StringBuilder();

		InputStream fIn = null;
		InputStreamReader isr = null;
		BufferedReader input = null;

		try {
			fIn = context.getResources().getAssets()
			.open(fileName, Context.MODE_WORLD_READABLE);

			isr = new InputStreamReader(fIn);
			input = new BufferedReader(isr);

			String line = "";

			while ((line = input.readLine()) != null) {
				returnString.append(line);
			}

		}
		catch (Exception e) { e.getMessage(); }
		finally {
			try {
				if (isr != null) { isr.close(); }
				if (fIn != null) { fIn.close(); }
				if (input != null) { input.close(); }

			} catch (Exception e2) { e2.getMessage(); }
		}

		return returnString.toString();
	}

	public static void setScriptInstance(int instanceID) {
		script_instanceID = instanceID;
	}

	public static void callScriptFunc(int script_id, String key, String value) {
		GodotLib.calldeferred(script_id, "_receive_message", new Object[] { TAG, key, value });
	}

	public static void callScriptFunc(String key, String value) {
		if (script_instanceID == -1) {
			// Log.d(TAG, "Script instance not set");
			return;
		}

		GodotLib.calldeferred(script_instanceID, "_receive_message",
		new Object[] { TAG, key, value });
	}

	public static boolean checkGooglePlayService(Activity activity) {
		return true;
	}

	public static int script_instanceID = -1;
	private static final String TAG = "GooglePlay";
}
