
package com.godlevskyi.reactnative.smartconfig;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.List;
import android.app.Activity;
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
import com.espressif.iot.esptouch.IEsptouchListener;
import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch.util.TouchNetUtil;

public class RNSmartconfigNgModule extends ReactContextBaseJavaModule {
  private static final String TAG = "RNSmartconfigNgModule";
  private final ReactApplicationContext reactContext;
  private Activity thisActivity = getCurrentActivity();
  private Promise mConfigPromise;
  private String mSSID = "";
  private String mBSSID = "";
  private boolean isWifiConnected = false;
  private boolean is5GWifi = false;
  private IEsptouchListener myListener = new IEsptouchListener() {
    @Override
    public void onEsptouchResultAdded(final IEsptouchResult result) {
        onEsptoucResultAddedPerform(result);
    }
  };

  private EsptouchAsyncTask mTask;
  private boolean mReceiverRegistered = false;
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

  private void registerBroadcastReceiver() {
    if (mReceiverRegistered) return;
    Log.i(TAG, "register receiver");
    IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    reactContext.registerReceiver(mReceiver, filter);
    mReceiverRegistered = true;
  }

  private void onWifiChanged(WifiInfo info) {
    boolean disconnected = info == null
            || info.getNetworkId() == -1
            || "<unknown ssid>".equals(info.getSSID());
    if (disconnected) {
      mSSID = "";
      mBSSID = "";
      // TODO: event
      isWifiConnected = false;

      if (mTask != null) {
        mTask.cancelEsptouch();
        mTask = null;
        if (mConfigPromise != null) mConfigPromise.reject("no Wifi connection");
      }
    } else {
      // TODO: event
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
          // TODO: event
          Log.i(TAG, "Connected 5G wifi. Device does not support 5G");
          is5GWifi = true;
        } else {
          // TODO: event
          is5GWifi = false;
        }
      }
    }
  }

  private void onEsptoucResultAddedPerform(final IEsptouchResult result) {
    // String text = result.getBssid() + " is connected to the wifi";
    // TODO: emmit event
    Log.i(TAG, "EsptoucResultAdded");
  }

  @ReactMethod
  public void init() {
    registerBroadcastReceiver();
  }

  @ReactMethod
  public void start(String pwd, int broadcastType, int deviceCount, Promise promise) {
    mConfigPromise = promise;
    if (!mReceiverRegistered) {
      mConfigPromise.reject("Smartconfig is not initialized");
      return;
    }
    if (is5GWifi) {
      mConfigPromise.reject("Device do not support 5G Wifi, please make sure the currently connected Wifi is 2.4G");
      return;
    }
    if (!isWifiConnected) {
      mConfigPromise.reject("No Wifi connection");
      return;
    }
    
    byte[] ssid = ByteUtil.getBytesByString(mSSID);
    byte[] password = ByteUtil.getBytesByString(pwd);
    byte[] bssid = TouchNetUtil.parseBssid2bytes(mBSSID);
    byte[] devices = {(byte)(deviceCount >> 24), (byte)(deviceCount >> 16), (byte)(deviceCount >> 8), (byte) deviceCount};
    byte[] broadcast = {(byte)(broadcastType >> 24), (byte)(broadcastType >> 16), (byte)(broadcastType >> 8), (byte) broadcastType};

    if (mTask != null) {
      mTask.cancelEsptouch();
    }
    mTask = new EsptouchAsyncTask(thisActivity);
    mTask.execute(ssid, bssid, password, devices, broadcast);
  }
  
  @ReactMethod
  public void finish() {
    mConfigPromise = null;
    if (mTask != null) {
      mTask.cancelEsptouch();
    }
    if (mReceiverRegistered) {
      reactContext.unregisterReceiver(mReceiver);
      Log.i(TAG, "finished and unregisterReceiver");
    }
    mReceiverRegistered = false;
  }

  @ReactMethod
  public void getNetInfo(Promise promise) {
    WritableMap map = Arguments.createMap();
    map.putString("ssid", mSSID);
    map.putString("bssid", mBSSID);
    promise.resolve(map);
  }

  @Override
  public String getName() {
    return "Smartconfig";
  }

  private class EsptouchAsyncTask extends AsyncTask<byte[], Void, List<IEsptouchResult>> {
    private WeakReference<Activity> mActivity;
    private IEsptouchTask mEsptouchTask;

    EsptouchAsyncTask(Activity activity) {
      mActivity = new WeakReference<>(activity);
    }

    void cancelEsptouch() {
      cancel(true);
      if (mEsptouchTask != null) {
        mEsptouchTask.interrupt();
      }
    }

    @Override
    protected void onPreExecute() {
      // Nothing
    }

    @Override
    protected List<IEsptouchResult> doInBackground(byte[]... params) {
        int taskResultCount;
        byte[] apSsid = params[0];
        byte[] apBssid = params[1];
        byte[] apPassword = params[2];
        byte[] deviceCountData = params[3];
        byte[] broadcastData = params[4];
        taskResultCount = ByteBuffer.wrap(deviceCountData).getInt();
        taskResultCount = taskResultCount <= 0 ? -1 : taskResultCount;
        mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, reactContext);
        mEsptouchTask.setPackageBroadcast(ByteBuffer.wrap(broadcastData).getInt() == 1);
        mEsptouchTask.setEsptouchListener(myListener);
        return mEsptouchTask.executeForResults(taskResultCount);
    }

    @Override
    protected void onPostExecute(List<IEsptouchResult> result) {
      if (result == null) {
        Log.i(TAG, "Create Esptouch task failed, the EspTouch port could be used by other thread");
        mConfigPromise.reject("Create Esptouch task failed");
        return;
      }

      IEsptouchResult firstResult = result.get(0);
      // check whether the task is cancelled and no results received
      if (!firstResult.isCancelled()) {
        if (firstResult.isSuc()) {
          WritableArray ret = Arguments.createArray();
          for (IEsptouchResult touchResult : result) {
            Log.d(TAG, "Host name: " + touchResult.getInetAddress().getHostAddress());
            WritableMap map = Arguments.createMap();
            map.putString("bssid", touchResult.getBssid());
            map.putString("ipv4", touchResult.getInetAddress().getHostAddress());
            ret.pushMap(map);
          }
          mConfigPromise.resolve(ret);
        } else {
          Log.d(TAG, "No devices smartconfig");
          mConfigPromise.reject("No devices");
        }
        mTask = null;
      } else {
        Log.d(TAG, "Smartconfig canceled");
        mConfigPromise.reject("Smartconfig canceled");
      }
    }
  }
}
