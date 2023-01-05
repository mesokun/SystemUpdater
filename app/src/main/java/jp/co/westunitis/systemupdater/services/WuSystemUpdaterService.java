package jp.co.westunitis.systemupdater.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UpdateEngine;
import android.util.Log;

import com.ts.hmd.update.sdk.HmdUpdateManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.co.westunitis.systemupdater.IWuSystemUpdater;
import jp.co.westunitis.systemupdater.IWuSystemUpdaterListener;
import jp.co.westunitis.systemupdater.R;
import jp.co.westunitis.systemupdater.UpdateConfig;
import jp.co.westunitis.systemupdater.UpdateManager;
import jp.co.westunitis.systemupdater.UpdaterState;
import jp.co.westunitis.systemupdater.services.http.HttpClient;
import jp.co.westunitis.systemupdater.services.http.IRequestApiEndpoint;
import jp.co.westunitis.systemupdater.services.models.resonses.CheckSystemUpdateResponse;
import jp.co.westunitis.systemupdater.services.models.resonses.RequestOnetimeApiTokenResponse;
import jp.co.westunitis.systemupdater.services.params.WuSystemUpdaterState;
import jp.co.westunitis.systemupdater.services.parcelables.UpdateInfo;
import jp.co.westunitis.systemupdater.util.DeviceInfoUtil;
import jp.co.westunitis.systemupdater.util.LogUtil;
import jp.co.westunitis.systemupdater.util.MD5;
import jp.co.westunitis.systemupdater.util.UpdateEngineErrorCodes;
import jp.co.westunitis.systemupdater.util.UpdateEngineStatuses;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WuSystemUpdaterService extends Service {
    private static final String TAG = "WuSystemUpdaterService";

    private final UpdateManager mUpdateManager =
            new UpdateManager(new UpdateEngine(), new Handler());

    private RemoteCallbackList<IWuSystemUpdaterListener> mListener = new RemoteCallbackList<>();
    private String mDomainURL;
    private String mBuildCode;
    private int mUpdateImageSize = -1;
    private UpdateConfig mUpdateConfig;
    private int mUpdateEngineState = -1;
    private int mUpdateEngineRunningStatus = -1;
    private Handler mHandler;
    private String mOnetimeClientKey;
    private String mOnetimeSystemImageUrl;
    private String mUpdateImageMD5;
    private BackgroundDownloader mDownloader;
    private HmdUpdateManager mHmdUpdateManager = null;

    private WuSystemUpdaterState mState = new WuSystemUpdaterState(WuSystemUpdaterState.IDLE);

    public static final int DOWNLOAD_SUCCEEDED = 0;
    public static final int DOWNLOAD_CANCELED = -1;
    public static final int DOWNLOAD_FAILED = -2;
    public static final int DOWNLOAD_FILE_ERROR = -3;
    public static final int DOWNLOAD_URL_EXPIRED = -4;

    @Override
    public void onCreate() {
        super.onCreate();

        LogUtil.d(TAG, "onCreate()");

        mHandler = new Handler();
        this.mUpdateManager.setOnStateChangeCallback(this::onUpdaterStateChange);
        this.mUpdateManager.setOnEngineStatusUpdateCallback(this::onEngineStatusUpdate);
        this.mUpdateManager.setOnEngineCompleteCallback(this::onEnginePayloadApplicationComplete);
        this.mUpdateManager.setOnProgressUpdateCallback(this::onProgressUpdate);

        // Binding to UpdateEngine invokes onStatusUpdate callback,
        // persisted updater state has to be loaded and prepared beforehand.
        this.mUpdateManager.bind();

        // Initialize HMD Updater to get the Neckband version
        mHmdUpdateManager = HmdUpdateManager.getInstance(getApplicationContext(), null);

        // set status
        setIdle();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "onStartCommand() intent:" + intent.getAction() +
                " flags:" + flags + " startId:" + startId);

        Context context = getApplicationContext();
        String channelId = "default";
        String title = context.getString(R.string.app_name);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager notificationManager =
                (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                channelId, title, NotificationManager.IMPORTANCE_DEFAULT);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(context, channelId)
                    .setContentTitle(title)
                    .setContentText("SystemUpdater")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .build();

            startForeground(1, notification);
        } else {
            LogUtil.d(TAG, "######## onStartCommand() notificationManager is null ########");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        LogUtil.d(TAG, "onDestroy()");

        if (mHmdUpdateManager != null) {
            mHmdUpdateManager.releaseData();
            mHmdUpdateManager = null;
        }

        if (mDownloader != null) {
            mDownloader.cancel(true);
            mDownloader = null;
        }
        this.mUpdateManager.setOnEngineStatusUpdateCallback(null);
        this.mUpdateManager.setOnProgressUpdateCallback(null);
        this.mUpdateManager.setOnEngineCompleteCallback(null);
        this.mUpdateManager.unbind();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        LogUtil.d(TAG, "onBind()");
        return mStub;
    }

    private final IWuSystemUpdater.Stub mStub = new IWuSystemUpdater.Stub() {
        @Override
        public void registerListener(IWuSystemUpdaterListener listener) throws RemoteException {
            LogUtil.d(TAG, "registerListener() listener:" + listener.toString());
            mHandler.post(() -> {
                if (listener != null) {
                    mListener.register(listener);
                    try {
                        // Notify current status
                        listener.onChangeStatus(getState());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void unregisterListener(IWuSystemUpdaterListener listener) throws RemoteException {
            LogUtil.d(TAG, "unregisterListener() listener:" + listener.toString());
            mHandler.post(() -> {
                mListener.unregister(listener);
            });
        }

        @Override
        public void registerCheckUpdateDomain(String domain) throws RemoteException {
            LogUtil.d(TAG, "registerCheckUpdateDomain: domain=" + domain);
            mDomainURL = domain;
        }

        @Override
        public boolean checkUpdate(String version) throws RemoteException {
            LogUtil.d(TAG, "checkUpdate: current state:" + getStateText() +
                    " version=" + version + " domain:" + mDomainURL);

            if ((isIdle() || isUpdateAvailable()) &&
                    null != mDomainURL && mDomainURL.length() > 10 &&
                    null != getNbcVersion()) {
                requestOnetimeApiToken();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean downloadAndApplyUpdate() throws RemoteException {
            LogUtil.d(TAG, "downloadAndApplyUpdate() current state:" + getStateText() +
                    " domain:" + mDomainURL + " buildCode:" + mBuildCode);

            if (isUpdateAvailable()) {
                downloadSystemImage();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean cancel() throws RemoteException {
            if (isDownloading()) {
                LogUtil.d(TAG, "cancel() returns true: current state = " + getStateText());
                // set status
                mHandler.post(() -> setCancel());
                return true;
            } else {
                LogUtil.d(TAG, "cancel() returns false: current state = " + getStateText());
                return false;
            }
        }

        @Override
        public int getStatus() throws RemoteException {
            LogUtil.d(TAG, "getStatus() current state:" + getStateText());
            return getState();
        }
    };

    @Nullable
    private String getNbcVersion() {
        if (mHmdUpdateManager == null) {
            return null;
        }

        String version = mHmdUpdateManager.getNeckBandVersion();
        if (version == null || version.length() < 8) {
            return null;
        }

        return version.substring(0, 8);
    }

    private synchronized void setState(int state) {
        LogUtil.d(TAG, "status changes:" + getStateText() +
                " -> " + mState.getStateText(state));
        try {
            mState.set(state);
        } catch (WuSystemUpdaterState.InvalidTransitionException e) {
            e.printStackTrace();
        }

        // Notify status changed
        onChangeStatus(state);
    }

    private void setIdle() {
        setState(WuSystemUpdaterState.IDLE);
    }

    private void setCheckingUpdate() {
        setState(WuSystemUpdaterState.CHECKING_UPDATE);
    }

    private void setUpdateAvailable() {
        setState(WuSystemUpdaterState.UPDATE_AVAILABLE);
    }

    private void setDownloading() {
        setState(WuSystemUpdaterState.DOWNLOADING);
    }

    private void setCancel() {
        setState(WuSystemUpdaterState.CANCEL_DOWNLOADING);
    }

    private void setApplyingUpdate() {
        setState(WuSystemUpdaterState.APPLYING_UPDATE);
    }

    private void setFinalizing() {
        setState(WuSystemUpdaterState.FINALIZING);
    }

    private void setWaitingReboot() {
        setState(WuSystemUpdaterState.WAITING_REBOOT);
    }

    private int getState() {
        return mState.get();
    }

    private boolean isIdle() {
        return getState() == WuSystemUpdaterState.IDLE;
    }

    private boolean isUpdateAvailable() {
        return getState() == WuSystemUpdaterState.UPDATE_AVAILABLE;
    }

    private boolean isDownloading() {
        return getState() == WuSystemUpdaterState.DOWNLOADING;
    }

    private boolean isCanceled() {
        return getState() == WuSystemUpdaterState.CANCEL_DOWNLOADING;
    }

    private String getStateText() {
        return WuSystemUpdaterState.getStateText(getState());
    }

    private void onResponseCheckUpdate(boolean result, boolean available, int size) {
        LogUtil.d(TAG, "onResponseCheckAppUpdate() result:" + result +
                ", available:" + available + ", size:" + size + " bytes");

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).onResponseCheckUpdate(result, available, size,
                        null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onResponseCheckUpdate(boolean result, boolean available, int size,
                                       UpdateInfo[] infos) {
        LogUtil.d(TAG, "onResponseCheckAppUpdate() result:" + result +
                ", available:" + available + ", size:" + size +
                " bytes, number of updates:" + infos.length);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                Bundle bundle = new Bundle(getClass().getClassLoader());
                bundle.putParcelableArray("system_update_info", infos);
                mListener.getBroadcastItem(i).onResponseCheckUpdate(result, available, size,
                        bundle);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onProgressDownload(int downloaded, int total) {
        LogUtil.d(TAG, "onProgressDownload() downloaded:" + downloaded + " total:" + total);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).notifyDownloadProgress(downloaded, total);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onFinishDownload(boolean result, int downloadSize, int reason) {
        LogUtil.d(TAG, "onFinishDownload() result:" + result +
                " downloadSize:" + downloadSize + " reason:" + reason);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).onFinishDownload(result, downloadSize, reason);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onProgressSystemUpdate(double progress) {
        LogUtil.d(TAG, "onProgressSystemUpdate() progress:" + progress);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).notifySystemUpdateProgress(progress);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onResponseSystemUpdate(boolean result, int reason) {
        LogUtil.d(TAG, "onResponseSystemUpdate() result:" + result + " reason:" + reason);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).onFinishSystemUpdate(result, reason);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void onChangeStatus(int status) {
        LogUtil.d(TAG, "onChangeStatus() status:" + status);

        int numListeners = mListener.beginBroadcast();

        for (int i=0; i<numListeners; i++) {
            try {
                mListener.getBroadcastItem(i).onChangeStatus(status);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        mListener.finishBroadcast();
    }

    private void requestOnetimeApiToken() {
        mHandler.post(() ->{
            // set status
            setCheckingUpdate();
            // get client key string
            mOnetimeClientKey = getOnetimeClientKey();
            LogUtil.d(TAG, "requestOnetimeApiToken: key=" + mOnetimeClientKey);
            // requests update api token
            IRequestApiEndpoint endpoint = HttpClient.getEndpoint(mDomainURL,
                    HttpClient.getClient(mOnetimeClientKey));
            endpoint.requestOnetimeApiToken(DeviceInfoUtil.getSerialNumber()).enqueue(mOnetimeApiTokenResponse);
        });
    }

    private String getOnetimeClientKey() {
        return UUID.randomUUID().toString();
    }

    private Callback<RequestOnetimeApiTokenResponse> mOnetimeApiTokenResponse =
            new Callback<RequestOnetimeApiTokenResponse>() {
                @Override
                public void onResponse(Call<RequestOnetimeApiTokenResponse> call,
                                       Response<RequestOnetimeApiTokenResponse> response) {
                    okhttp3.Response res = response.raw();
                    LogUtil.d(TAG, "Raw : " + res.toString());

                    mHandler.post(() -> {
                        if (response.isSuccessful()) {
                            RequestOnetimeApiTokenResponse apiToken = response.body();
                            if (apiToken != null) {
                                LogUtil.d(TAG, "Token:" + apiToken.mToken);
                                checkSystemUpdate(mOnetimeClientKey, apiToken.mToken);
                            } else {
                                LogUtil.d(TAG, "Token Resonse Error");
                                onResponseCheckUpdate(false, false, 0);
                                // set status
                                setIdle();
                            }
                        } else {
                            // in case of response is not succeeded
                            LogUtil.d(TAG, "Response Error: " + response.code());
                            onResponseCheckUpdate(false, false, 0);
                            // set status
                            setIdle();
                        }
                    });
                }

                @Override
                public void onFailure(Call<RequestOnetimeApiTokenResponse> call, Throwable t) {
                    LogUtil.d(TAG, "RequestOnetimeApiTokenResponse onFailure : " + call.request());
                    mHandler.post(() -> {
                        onResponseCheckUpdate(false, false, 0);
                        // set status
                        setIdle();
                    });
                }
            };

    private void checkSystemUpdate(String key, String token) {
        LogUtil.d(TAG, "checkSystemUpdate: key=" + key + " token=" + token +
                " domain:" + mDomainURL);
        IRequestApiEndpoint endpoint = HttpClient.getEndpoint(mDomainURL,
                HttpClient.getClient(key, token));
        endpoint.checkSystemUpdate("").enqueue(mCheckUpdateResponse);
    }

    private Callback<CheckSystemUpdateResponse> mCheckUpdateResponse =
            new Callback<CheckSystemUpdateResponse>() {
        @Override
        public void onResponse(Call<CheckSystemUpdateResponse> call,
                               Response<CheckSystemUpdateResponse> response) {
            okhttp3.Response res = response.raw();
            LogUtil.d(TAG, "Raw : " + res.toString());

            mHandler.post(() -> {
                if (response.isSuccessful()) {
                    CheckSystemUpdateResponse update = response.body();
                    if (update != null) {
                        if (update.mUpdates.length > 0) {
                            for (int i = 0; i < update.mUpdates.length; i++) {
                                LogUtil.d(TAG, "build code[" + i + "] : " + update.mUpdates[i].mBuildCode);
                                LogUtil.d(TAG, "hash code[" + i + "] : " + update.mUpdates[i].mHash);
                                LogUtil.d(TAG, "note[" + i + "] : " + update.mUpdates[i].mNote);
                                LogUtil.d(TAG, "size[" + i + "] : " + update.mUpdates[i].mSize);
                                LogUtil.d(TAG, "full update[" + i + "] : " + update.mUpdates[i].mFullUpdated);
                                LogUtil.d(TAG, "url[" + i + "] : " + update.mUpdates[i].mUrl);
                            }
                            mBuildCode = update.mUpdates[0].mBuildCode;
                            String neckVersion = getNbcVersion();
                            if (null == neckVersion) {
                                onResponseCheckUpdate(true, false, 0);
                                // set status
                                setIdle();
                            }

                            try {
                                Integer verNext = Integer.valueOf(mBuildCode);
                                Integer verCurrent = Integer.valueOf(neckVersion);
                                LogUtil.d(TAG, "current version:" + verCurrent +
                                        " next version:" + verNext);

                                if (verNext > verCurrent) {
                                    mUpdateImageSize = update.mUpdates[0].mSize;
                                    mOnetimeSystemImageUrl = update.mUpdates[0].mUrl;
                                    mUpdateImageMD5 = update.mUpdates[0].mHash;

                                    UpdateInfo[] infos = new UpdateInfo[1];
                                    infos[0] = new UpdateInfo();
                                    infos[0].mBuildCode = update.mUpdates[0].mBuildCode;
                                    infos[0].mHash = update.mUpdates[0].mHash;
                                    infos[0].mNote = update.mUpdates[0].mNote;
                                    infos[0].mDownloadSize = update.mUpdates[0].mSize;
                                    infos[0].mFullUpdated = update.mUpdates[0].mFullUpdated;

                                    onResponseCheckUpdate(true, true,
                                            mUpdateImageSize, infos);
                                    // set status
                                    setUpdateAvailable();
                                } else {
                                    onResponseCheckUpdate(true, false, 0);
                                    // set status
                                    setIdle();
                                }
                            } catch (NumberFormatException e) {
                                onResponseCheckUpdate(true, false, 0);
                                // set status
                                setIdle();
                            }
                        } else {
                            LogUtil.d(TAG, "No system update information");
                            onResponseCheckUpdate(true, false, 0);
                            // set status
                            setIdle();
                        }
                    } else {
                        LogUtil.d(TAG, "response.body() returned null");
                        onResponseCheckUpdate(false, false, 0);
                        // set status
                        setIdle();
                    }
                } else {
                    // in case of response is not succeeded
                    LogUtil.d(TAG, "Response Error: " + response.code());
                    onResponseCheckUpdate(false, false, 0);
                    // set status
                    setIdle();
                }
            });
        }

        @Override
        public void onFailure(Call<CheckSystemUpdateResponse> call, Throwable t) {
            LogUtil.d(TAG, "checkSystemUpdate onFailure : " + call.request());
            mHandler.post(() -> {
                onResponseCheckUpdate(false, false, 0);
                // set status
                setIdle();
            });
        }
    };

    private void downloadSystemImage() {
        LogUtil.d(TAG, "downloadSystemImage buildCode=" + mBuildCode +
                " URL:" + mOnetimeSystemImageUrl);

        mHandler.post(() -> {
            // set status
            setDownloading();
            // initialize downloader thread
            mDownloader = new BackgroundDownloader();
            mDownloader.execute();
        });
    }

    public class BackgroundDownloader extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... strings) {
            LogUtil.d(TAG, "request download:" + mOnetimeSystemImageUrl);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(mOnetimeSystemImageUrl)
                    .get()
                    .build();

            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    LogUtil.d(TAG, "System Image download succeeded.");

                    int ret = writeResponseBodyToFile(response.body());

                    // check if canceled?
                    if (isCanceled()) {
                        ret = DOWNLOAD_CANCELED;
                    }

                    if (ret == DOWNLOAD_SUCCEEDED) {
                        LogUtil.d(TAG, "System Image writeResponseBodyToFile() succeeded.");
                        // Obtain Configuration from zip file
                        mUpdateConfig = UpdateConfig.fromZip(getApplicationContext(), mBuildCode);
                        // Notify download succeeded.
                        onFinishDownload(true, mUpdateImageSize, DOWNLOAD_SUCCEEDED);
                        // check
                        if (DeviceInfoUtil.isInfoLinker()) {
                            // set status
                            setApplyingUpdate();
                            // start update
                            applyUpdate(mUpdateConfig);
                        } else {
                            // set status
                            setIdle();
                        }
                    } else {
                        LogUtil.d(TAG, "System Image writeResponseBodyToFile() failed or canceled....");
                        // Notify download failure.
                        onFinishDownload(false, 0, ret);
                        // set status
                        setIdle();
                    }
                } else if (response.code() == 403) {
                    LogUtil.d(TAG, "System Image URL has expired...");
                    // Notify download failure.
                    onFinishDownload(false, 0, DOWNLOAD_URL_EXPIRED);
                    // set status
                    setIdle();
                } else {
                    LogUtil.d(TAG, "System Image download failed...");
                    // Notify download failure.
                    onFinishDownload(false, 0, DOWNLOAD_FAILED);
                    // set status
                    setIdle();
                }
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
                // Notify download failure.
                onFinishDownload(false, 0, DOWNLOAD_FAILED);
                // set status
                setIdle();
            }
            return null;
        }
    }

    private int writeResponseBodyToFile(final ResponseBody body) {
        try {
            File file = new File(getExternalFilesDir(null).toString() +
                    File.separator + "/SystemImg/" + mBuildCode + ".zip");
            LogUtil.d(TAG, "System Image file path=" + file.toString());

            if (!file.exists()) {
                file.getParentFile().mkdir();
            }

            OutputStream outputStream = null;
            InputStream inputStream = body.byteStream();

            try {
                byte[] buf = new byte[1024 * 16];
                outputStream = new FileOutputStream(file);

                int szRead = -1;
                int szTotal = 0;
                int szNotify = 0;
                int szInterval = (int)(mUpdateImageSize * 0.05);

                while ((szRead = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, szRead);
                    szTotal += szRead;
                    // 通知が飛びすぎるため間引く
                    if (szTotal > (szNotify + szInterval)) {
                        LogUtil.d(TAG, "outputStream.write() wrote " + szRead + " bytes" +
                                " download size:" + szTotal + " / " + mUpdateImageSize +
                                " bytes" + " Notification Interval:" + szInterval);
                        szNotify = szTotal;
                        if (isCanceled()) {
                            return DOWNLOAD_CANCELED;
                        } else {
                            // Notify progress of download.
                            onProgressDownload(szNotify, mUpdateImageSize);
                        }
                    }
                }

                outputStream.flush();
                LogUtil.d(TAG, "outputStream.flush() flushed:" + szTotal + " bytes");

                if (isCanceled()) {
                    return DOWNLOAD_CANCELED;
                } else {
                    if (!MD5.checkMD5(mUpdateImageMD5, file)) {
                        LogUtil.d(TAG, "MD5 check: NG!!!!!!!!!");
                        return DOWNLOAD_FILE_ERROR;
                    }
                }

                // Notify progress of download.
                onProgressDownload(szTotal, mUpdateImageSize);

                return DOWNLOAD_SUCCEEDED;
            } catch (IOException e) {
                e.printStackTrace();
                return DOWNLOAD_FAILED;
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return DOWNLOAD_FAILED;
        }
    }

    private void onUpdaterStateChange(int state) {
        Log.i(TAG, "UpdaterStateChange state="
                + UpdaterState.getStateText(state)
                + "/" + state);
        mUpdateEngineState = state;
    }

    private void onEngineStatusUpdate(int status) {
        Log.i(TAG, "StatusUpdate - status="
                + UpdateEngineStatuses.getStatusText(status)
                + "/" + status);
        // Judge if it starts finalizing
        if (status == 5 && mUpdateEngineRunningStatus < 5) {
            // set status
            mHandler.post(() -> setFinalizing());
        }
        mUpdateEngineRunningStatus = status;
    }

    private void onEnginePayloadApplicationComplete(int errorCode) {
        final String completionState = UpdateEngineErrorCodes.isUpdateSucceeded(errorCode)
                ? "SUCCESS"
                : "FAILURE";
        Log.i(TAG,
                "PayloadApplicationCompleted - errorCode="
                        + UpdateEngineErrorCodes.getCodeName(errorCode) + "/" + errorCode
                        + " " + completionState);

        mHandler.post(() -> {
            if (completionState.equals("FAILURE")) {
                // Notify result of System Update.
                onResponseSystemUpdate(false, -2);
                // set status
                setIdle();
                return;
            }

            if (UpdateEngineErrorCodes.getCodeName(errorCode).equals("UPDATED_BUT_NOT_ACTIVE") &&
                    UpdaterState.getStateText(mUpdateEngineState).equals("SLOT_SWITCH_REQUIRED")) {
                LogUtil.d(TAG, "onEnginePayloadApplicationComplete requests setSwitchSlotOnReboot()");

                // Start switching slot (a <-> b).
                mUpdateManager.setSwitchSlotOnReboot();
            } else if (UpdateEngineErrorCodes.getCodeName(errorCode).equals("SUCCESS") &&
                    UpdaterState.getStateText(mUpdateEngineState).equals("REBOOT_REQUIRED")) {
                LogUtil.d(TAG, "onEnginePayloadApplicationComplete requests REBOOT!!!!!");

                // Notify progress of system update
                onProgressSystemUpdate(1.00d);

                // Notify result of System Update.
                onResponseSystemUpdate(true, 0);

                // set status
                setWaitingReboot();

                // Reboot the device after finishing switching slot.
                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                pm.reboot(null);
            }
        });
    }

    private void onProgressUpdate(double progress) {
        LogUtil.d(TAG, "onProgressUpdate status:" +
                UpdaterState.getStateText(mUpdateEngineState) + " progress:" + progress);

        BigDecimal value = BigDecimal.valueOf(progress);
        // Notify progress of system update
        if (mUpdateEngineRunningStatus == 3 && value.compareTo(BigDecimal.valueOf(0.01)) >= 0) {
            mHandler.post(() -> onProgressSystemUpdate(value.doubleValue()));
        }
    }

    private void applyUpdate(UpdateConfig config) {
        LogUtil.d(TAG, "applyUpdate starts update......");

        try {
            // Start System Update.
            mUpdateManager.applyUpdate(this, config);
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to apply update " + config.getName(), e);
            // Notify result of System Update.
            onResponseSystemUpdate(false, -2);
            // set status
            setIdle();
        }
    }
}