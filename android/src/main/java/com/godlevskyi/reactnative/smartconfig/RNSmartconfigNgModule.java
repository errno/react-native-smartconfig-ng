
package com.godlevskyi.reactnative.smartconfig;

import java.util.List;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.util.ByteUtil;

public class RNSmartconfigNgModule extends ReactContextBaseJavaModule {
  private static final String TAG = "RNSmartconfigNgModule";
  private final ReactApplicationContext reactContext;
  private String mSSID = "";
  private String mBSSID = "";
  private boolean is5GWifi = false;
  private boolean isWifiConnected = false;
  private boolean mReceiverRegistered = false;
  private Promise mConfigPromise;
  private EsptouchAsyncTask mTask;

    // method Promise.reject(String,String) is not applicable
    //   (argument mismatch; int cannot be converted to String)
    // method Promise.reject(String,Throwable) is not applicable
    //   (argument mismatch; int cannot be converted to String)
    // method Promise.reject(Throwable,WritableMap) is not applicable
    //   (argument mismatch; int cannot be converted to Throwable)
    // method Promise.reject(String,WritableMap) is not applicable
    //   (argument mismatch; int cannot be converted to String)

  private BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action == null) return;

      WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      assert wifiManager != null;

      switch (action) {
        case WifiManager.NETWORK_STATE_CHANGED_ACTION:
          WifiInfo wifiInfo;
          if (intent.hasExtra(WifiManager.EXTRA_WIFI_INFO)) {
            wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
          } else {
            wifiInfo = wifiManager.getConnectionInfo();
          }
          onWifiChanged(wifiInfo);
          break;
      }
    }
  };

  public RNSmartconfigNgModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "Smartconfig";
  }

  @ReactMethod
  public void initESPTouch() {
    if (mReceiverRegistered) return;
    mReceiverRegistered = true;
    Log.i(TAG, "Register receiver");
    IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    reactContext.registerReceiver(mReceiver, filter);
  }

  @ReactMethod
  public void startSmartConfig(String pwd, int broadcastType, Promise promise) {
    mConfigPromise = promise;
    if (!mReceiverRegistered) {
      promise.reject("SmartConfig is not initialized");
      return;
    }
    if (is5GWifi) {
      promise.reject("Device don not support 5G Wifi, please make sure the currently connected Wifi is 2.4G");
      return;
    }
    if (!isWifiConnected) {
      promise.reject("No Wifi connection");
      return;
    }

    if (mTask != null) {
      mTask.cancelEsptouch();
    }
    mTask = new EsptouchAsyncTask(new TaskListener() {
      @Override
      public void onFinished(List<IEsptouchResult> result) {
        WritableArray ret = Arguments.createArray();
        Boolean resolved = false;
        try {
          for (IEsptouchResult resultInList : result) {
            Log.d(TAG, "for (IEsptouchResult resultInList : result)");
            if (!resultInList.isCancelled() && resultInList.getBssid() != null) {
              WritableMap map = Arguments.createMap();
              map.putString("bssid", resultInList.getBssid());
              map.putString("ipv4", resultInList.getInetAddress().getHostAddress());
              Log.d(TAG, "Host name: " + resultInList.getInetAddress().getHostAddress());
              ret.pushMap(map);
              resolved = true;
              if (!resultInList.isSuc()) {
                Log.d(TAG, "Not successful!");
                break;
              }
            }
          }

          if (resolved) {
            Log.d(TAG, "Success run smartconfig");
            mConfigPromise.resolve(ret);
          } else {
            Log.d(TAG, "Error run smartconfig");
            mConfigPromise.reject("Smartconfig failed");
          }
        } catch (Exception e) {
          Log.d(TAG, "Error, Smartconfig could not complete!");
          mConfigPromise.reject("new Exception()", e);
        }
      }
    });
    mTask.execute(mSSID, mBSSID, pwd, "YES", "1");
  }

  @ReactMethod
  public void getNetInfo(Promise promise) {
    WritableMap map = Arguments.createMap();
    map.putString("ssid", mSSID);
    map.putString("bssid", mBSSID);
    promise.resolve(map);
  }

  @ReactMethod
  public void finish() {
    mConfigPromise = null;
    if (mTask != null) {
      mTask.cancelEsptouch();
    }
    if (mReceiverRegistered) {
      reactContext.unregisterReceiver(mReceiver);
      Log.i(TAG, "Config and unregisterReceiver finished");
    }
    mReceiverRegistered = false;
  }

  public interface TaskListener {
    public void onFinished(List<IEsptouchResult> result);
  }

  private void onWifiChanged(WifiInfo info) {
    boolean disconnected = info == null
            || info.getNetworkId() == -1
            || "<unknown ssid>".equals(info.getSSID());
    if (disconnected) {
      Log.i(TAG, "No Wifi connection");
      mSSID = "";
      mBSSID = "";
      isWifiConnected = false;
      if (mTask != null) {
        mTask.cancelEsptouch();
        mTask = null;
        if (mConfigPromise != null) {
          mConfigPromise.reject("no Wifi connection");
        }
      }
    } else {
      isWifiConnected = true;
      mSSID = info.getSSID();
      if (mSSID.startsWith("\"") && mSSID.endsWith("\"")) {
        mSSID = mSSID.substring(1, mSSID.length() - 1);
      }
      mBSSID = info.getBSSID();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        int frequency = info.getFrequency();
        if (frequency > 4900 && frequency < 5900) {
          // Connected 5G wifi. Device does not support 5G
          Log.i(TAG, "Connected 5G wifi. Device does not support 5G");
          is5GWifi = true;
        } else {
          is5GWifi = false;
        }
      }
    }
  }

  private class EsptouchAsyncTask extends AsyncTask<String, Void, List<IEsptouchResult>> {
    // without the lock, if the user tap confirm and cancel quickly enough,
    // the bug will arise. the reason is follows:
    // 0. task is starting created, but not finished
    // 1. the task is cancel for the task hasn't been created, it do nothing
    // 2. task is created
    // 3. Oops, the task should be cancelled, but it is running
    private final Object mLock = new Object();
    private IEsptouchTask mEsptouchTask;
    private final TaskListener taskListener;

    public EsptouchAsyncTask(TaskListener listener) {
      this.taskListener = listener;
    }

    void cancelEsptouch() {
      cancel(true);
      if (mEsptouchTask != null) {
          mEsptouchTask.interrupt();
      }
    }

    @Override
    protected void onPreExecute() {
      Log.d(TAG, "Begin task");
    }

    @Override
    protected List<IEsptouchResult> doInBackground(String... params) {
      Log.d(TAG, "Doing task ");
      int taskResultCount = -1;
      synchronized (mLock) {
        String apSsid = params[0];
        String apBssid = params[1];
        String apPassword = params[2];
        String isSsidHiddenStr = params[3];
        String taskResultCountStr = params[4];
        boolean isSsidHidden = false;
        if (isSsidHiddenStr.equals("YES")) {
          isSsidHidden = true;
        }
        taskResultCount = Integer.parseInt(taskResultCountStr);
        mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, reactContext);
      }
      return mEsptouchTask.executeForResults(taskResultCount);
    }

    @Override
    protected void onPostExecute(List<IEsptouchResult> result) {
      if (result == null) {
        Log.i(TAG, "Create Esptouch task failed, the EspTouch port could be used by other thread");
        mConfigPromise.reject("Smartconfig failed - EsptouchAsyncTask");
        return;
      }
      Log.d(TAG, "Result: " + result.size());
      IEsptouchResult firstResult = result.get(0);
      if (!firstResult.isCancelled()) {
        if (this.taskListener != null) {
          this.taskListener.onFinished(result);
        }
      }
      mTask = null;
    }
  }
}
