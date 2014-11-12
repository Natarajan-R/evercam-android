package io.evercam.androidapp.tasks;

import java.util.ArrayList;
import java.util.Iterator;

import io.evercam.API;
import io.evercam.Camera;
import io.evercam.EvercamException;
import io.evercam.User;
import io.evercam.androidapp.CamerasActivity;
import io.evercam.androidapp.EvercamPlayApplication;
import io.evercam.androidapp.MainActivity;
import io.evercam.androidapp.R;
import io.evercam.androidapp.custom.CustomedDialog;
import io.evercam.androidapp.dal.DbCamera;
import io.evercam.androidapp.dto.AppData;
import io.evercam.androidapp.dto.AppUser;
import io.evercam.androidapp.dto.EvercamCamera;
import io.evercam.androidapp.utils.PrefsManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class LoadCameraListTask extends AsyncTask<Void, Boolean, Boolean>
{
	private AppUser user;
	private CamerasActivity camerasActivity;
	private String TAG = "evercamplay-LoadCameraListTask";
	public boolean reload = false;

	public LoadCameraListTask(AppUser user, CamerasActivity camerasActivity)
	{
		this.user = user;
		this.camerasActivity = camerasActivity;
	}

	@Override
	protected void onPreExecute()
	{
		if (user != null)
		{
			API.setUserKeyPair(user.getApiKey(), user.getApiId());
		}
		else
		{
			EvercamPlayApplication.sendEventAnalytics(camerasActivity, R.string.category_error,
					R.string.action_error_load_camera, R.string.label_error_empty_user);
			EvercamPlayApplication.sendCaughtException(camerasActivity,
					camerasActivity.getString(R.string.label_error_empty_user));
			CustomedDialog.showUnexpectedErrorDialog(camerasActivity);
			cancel(true);
		}
	}

	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			boolean updateDB = false;

			// Step 1: Load camera list from Evercam
			Log.d(TAG, "Step 1: Load camera list from Evercam");
			// FIXME: Time consuming at this line
			AppData.evercamCameraList = new DbCamera(camerasActivity.getApplicationContext())
					.getCamerasByOwner(user.getUsername(), 500);

			ArrayList<Camera> cameras = User.getCameras(user.getUsername(), true, true);
			ArrayList<EvercamCamera> evercamCameras = new ArrayList<EvercamCamera>();
			for (io.evercam.Camera camera : cameras)
			{
				EvercamCamera evercamCamera = new EvercamCamera().convertFromEvercam(camera);

				// //Fill Evercam camera object to local camera object
				if (AppData.evercamCameraList.size() > 0)
				{
					matchLoop: for (EvercamCamera cameraInList : AppData.evercamCameraList)
					{
						if (camera.getId().equals(cameraInList.getCameraId()))
						{
							cameraInList.camera = camera;
							// Only jump out this loop.
							break matchLoop;
						}
					}
				}

				evercamCameras.add(evercamCamera);
			}

			// Step 2: Check if any new cameras different from local saved
			// cameras.
			Log.d(TAG, "Step 2: Check if any new cameras different from local saved cameras.");
			for (EvercamCamera camera : evercamCameras)
			{
				if (!AppData.evercamCameraList.contains(camera))
				{
					Log.d(TAG, "new camera detected!" + camera.toString() + "\n");
					updateDB = true;
					break;
				}
			}

			// Step 3: Check if any local camera no longer exists in Evercam
			Log.d(TAG, "Step 3: Check if any local camera no longer exists in Evercam");
			if (!updateDB)
			{
				for (EvercamCamera camera : AppData.evercamCameraList)
				{
					if (!evercamCameras.contains(camera))
					{
						Log.d(TAG, "camera deleted!" + camera.getCameraId());
						updateDB = true;
						break;
					}
				}
			}

			// Step 4: If any different camera, replace all local camera data.
			Log.d(TAG, "Step 4: If any different camera, replace all local camera data.");
			if (updateDB)
			{
				reload = true;

				AppData.evercamCameraList = evercamCameras;
				this.publishProgress(true);
				Log.d(TAG, "Updating db");
				DbCamera dbCamera = new DbCamera(camerasActivity);
				dbCamera.deleteCameraByOwner(user.getUsername());

				Iterator<EvercamCamera> iterator = AppData.evercamCameraList.iterator();
				while (iterator.hasNext())
				{
					dbCamera.addCamera(iterator.next());
				}
			}
			else
			{
				this.publishProgress(true);
			}

			return true;
		}
		catch (EvercamException e)
		{
			Log.e(TAG, e.getMessage());
		}
		return false;
	}

	@Override
	protected void onProgressUpdate(Boolean... canLoad)
	{
		Log.d(TAG, "Done");

		if (canLoad[0])
		{
			if (reload)
			{
				camerasActivity.removeAllCameraViews();
				camerasActivity.addAllCameraViews(true, true);
			}
		}
		else
		{
			if (!camerasActivity.isFinishing())
			{
				EvercamPlayApplication.sendCaughtException(camerasActivity,
						camerasActivity.getString(R.string.exception_failed_load_cameras));
				CustomedDialog.getAlertDialog(camerasActivity,
						camerasActivity.getString(R.string.msg_error_occurred),
						camerasActivity.getString(R.string.msg_exception),
						new DialogInterface.OnClickListener(){
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								dialog.dismiss();
								SharedPreferences sharedPrefs = PreferenceManager
										.getDefaultSharedPreferences(camerasActivity);
								PrefsManager.removeUserEmail(sharedPrefs);

								camerasActivity.startActivity(new Intent(camerasActivity,
										MainActivity.class));
								new LogoutTask(camerasActivity)
										.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

								if (camerasActivity.refresh != null)
								{
									camerasActivity.refresh.setActionView(null);
								}
							}
						}).show();
			}
		}
		if (camerasActivity.reloadProgressDialog != null)
		{
			camerasActivity.reloadProgressDialog.dismiss();
		}
	}

	@Override
	protected void onPostExecute(Boolean success)
	{

	}
}
