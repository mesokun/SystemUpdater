package jp.co.westunitis.systemupdater;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import static android.content.Context.BIND_AUTO_CREATE;

public class WuSystemUpdater {
    private static final String TAG = "SystemUpdater: ";

    private static boolean mBoundUpdaterService;

    /**
     * ダウンロードの完了通知で通知される失敗（成功）理由
     */
    public static final int DOWNLOAD_SUCCEEDED = 0;       /** ダウンロード成功 */
    public static final int DOWNLOAD_CANCELED = -1;       /** 途中でキャンセルされた */
    public static final int DOWNLOAD_FAILED = -2;         /** ダウンロード失敗 */
    public static final int DOWNLOAD_FILE_ERROR = -3;     /** ダウンロードしたファイルが異常 */
    public static final int DOWNLOAD_TIMEOUT_ERROR = -4;  /** タイムアウトエラー */

    /**
     * アップデートの完了通知で通知される失敗（成功）理由
     */
    public static final int UPDATE_SUCCEEDED = 0;           /** アップデート成功 */
    public static final int UPDATE_CANCELED = -1;           /** 途中でキャンセルされた */
    public static final int UPDATE_FAILED_INSTALL = -2;     /** インストールに失敗 */
    public static final int UPDATE_FAILED_SLOT_CHANGE = -3; /** スロット切り替えに失敗 */
    public static final int UPDATE_FAILED_REBOOT = -4;      /** デバイス再起動に失敗 */

    /**
     * ステータス取得で取得可能なステータス
     */
    public static final int STATUS_INVALID = -1;            /** ステータス取得できなかった */
    public static final int STATUS_IDLE = 0;                /** アイドル状態 */
    public static final int STATUS_ERROR = 1;               /** 復旧できないエラー状態 */
    public static final int STATUS_CHECKING_UPDATE = 2;     /** アップデート確認中 */
    public static final int STATUS_UPDATE_AVAILABLE = 3;    /** アップデート待ち状態 */
    public static final int STATUS_DOWNLOADING = 4;         /** ダウンロード中 */
    public static final int STATUS_CANCEL_DOWNLOADING = 5;  /** ダウンロードキャンセル要求中 */
    public static final int STATUS_APPLYING_UPDATE = 6;     /** アップデートインストール中 */
    public static final int STATUS_FINALIZING = 7;          /** アップデート後処理中 */
    public static final int STATUS_WAITING_REBOOT = 8;      /** アップデート完了後のReboot待ち状態 */

    /**
     * コンストラクタ（インスタンス取得）
     *
     * WuSystemUpdaterのインスタンスを作成し、システムアップデータサービスに接続しに行く。
     * システムアップデータサービスへの接続が完了し、利用可能な状態になると Callback.onServiceConnected() が
     * 呼び出される。その際にパラメータとして{@code WuSystemUpdater.Manager}クラスのインスタンスが渡される。
     * アプリ側からは、このインスタンスを通して各リクエストを行う。
     *
     * @param context 実行コンテキスト
     * @param callback コールバックのインスタンス
     */
    public WuSystemUpdater(@NonNull Context context, @NonNull Callback callback) {
        mContext = context;
        registerCallback(callback);

        Intent service = new Intent("jp.co.westunitis.systemupdater.WuSystemUpdaterService.ACTION_BIND");
        service.setPackage("jp.co.westunitis.systemupdater");

        ComponentName cn = mContext.startForegroundService(service);
        if (cn == null) {
            Log.d(TAG, "######## startForegroundService() returns null #########");
        } else {
            Log.d(TAG, "######## startForegroundService() returns componentName:" + cn.toString());
        }
    }

    public boolean connect() {
        return bind();
    }

    public void disconnect() {
        Log.d(TAG, "disconnect()");
        mCallback = null;
        if (mSystemUpdater != null) {
            try {
                mSystemUpdater.unregisterListener(mStub);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            //mSystemUpdater = null;
        }
        mBoundUpdaterService = false;
        if (mContext != null) {
            mContext.unbindService(mServiceConnection);
            mContext = null;
        }
    }

    /**
     * システムアップデータサービスから通知を受け取るためのインターフェイス
     */
    public interface Callback {
        /**
         * システムアップデータサービスとの接続完了して利用可能になったことを通知する。
         * パラメータとして、アプリからシステムアップデータサービスに対して各種リクエストするための
         * インターフェイスのインスタンスを渡す。
         *
         * @param manager システムアップデータサービスに対して各種リクエストするためのインターフェイス
         */
        void onServiceConnected(Manager manager);

        /**
         * システムアップデータサービスとの接続が切れた場合に通知する。
         * 以降、システムアップデータサービスは利用不可。
         */
        void onServiceDisconnected();

        /**
         * システムアップデートの有無を通知する。
         *
         * @param result true：アップデートチェック自体が正常に終了したことを示す、false：チェックに失敗
         * @param available true：システムアップデート有り、false：無し
         * @param expectedDownloadSize ダウンロードするファイルの予測サイズ（bytes）
         * @param wuSystemUpdateInfos 複数アップデート情報の配列が渡される（アップデート情報が一つの場合は要素数1の配列）
         */
        void onFinishCheckUpdate(boolean result, boolean available, int expectedDownloadSize,
                                 WuSystemUpdateInfo[] wuSystemUpdateInfos);

        /**
         * ダウンロードの途中経過通知
         *
         * @param downloaded ダウンロード済みのサイズ（bytes）
         * @param total 全体のサイズ（bytes）
         */
        void notifyDownloadProgress(int downloaded, int total);

        /**
         * ダウンロードの完了通知
         *
         * @param result true：ダウンロード成功、false：失敗
         * @param downloadSize ダウンロードしたファイルのサイズ（bytes）
         * @param desc ダウンロード失敗した場合の理由などの説明（以下のいずれかの値）
         *             {@code DOWNLOAD_SUCCEEDED} = 0;      ダウンロード成功
         *             {@code DOWNLOAD_CANCELED} = -1;      ダウンロードキャンセルされた
         *             {@code DOWNLOAD_FAILED} = -2;        ダウンロード失敗
         *             {@code DOWNLOAD_FILE_ERROR} = -3;    ダウンロードしたファイルが異常
         *             {@code DOWNLOAD_TIMEOUT_ERROR} = -4; タイムアウトエラー
         */
        void onFinishDownload(boolean result, int downloadSize, int desc);

        /**
         * システムアップデートの途中経過通知
         *
         * @param progress 進捗率（％）
         */
        void notifySystemUpdateProgress(double progress);

        /**
         * システムアップデートの完了通知
         *
         * @param result true：アップデート完了（この後すぐにデバイス再起動）、false：失敗
         * @param reason アップデート失敗時の理由（以下のいずれかの値）
         *               {@code UPDATE_SUCCEEDED} = 0;           アップデート成功
         *               {@code UPDATE_CANCELED} = -1:           途中でキャンセルされた
         *               {@code UPDATE_FAILED_INSTALL} = -2;     インストールに失敗
         *               {@code UPDATE_FAILED_SLOT_CHANGE} = -3; スロット切り替えに失敗
         *               {@code UPDATE_FAILED_REBOOT} = -4;      デバイス再起動に失敗
         */
        void onFinishSystemUpdate(boolean result, int reason);

        /**
         * アップデートステータス変化通知
         *
         * @param status 下記のいずれかを通知する。
         *               アイドル状態：{@code STATUS_IDLE} = 0;
         *               エラー状態：{@code STATUS_ERROR} = 1;
         *               アップデート確認中：{@code STATUS_CHECKING_UPDATE} = 2;
         *               アップデート待ち状態：{@code STATUS_UPDATE_AVAILABLE} = 3;
         *               ダウンロード中：{@code STATUS_DOWNLOADING} = 4;
         *               ダウンロードキャンセル要求中：{@code STATUS_CANCEL_DOWNLOADING} = 5;
         *               アップデートインストール中：{@code STATUS_APPLYING_UPDATE} = 6;
         */
        void onChangeStatus(int status);
    }

    /**
     * システムアップデータサービスに対して各種リクエストするためのインターフェイス
     */
    public class Manager {
        /**
         * ネットワーク経由でシステムアップデートがあるかどうかを確認する。
         * パラメータ domain で指定された接続先に対してシステムアップデート情報の取得をリクエストする。
         *
         * 指定されたドメインが間違っていたり、ネットワークに接続できない場合は false を返す。
         * また、既にこのAPIが実行済みの場合やアップデートが開始されている場合も false を返す。
         * 再度このAPIを呼び出したい場合は、一度 cancel() を実行して実行中の状態を初期化する必要がある。
         *
         * @return true：リクエスト実行できた。trueの場合は、あとで Callback.onFinishCheckUpdate() が
         *               呼び出されて、アップデートの有り無しが通知される。
         *         false：リクエスト実行できなかった。この場合は、コールバック呼び出しされない。
         * @param domain ログイン先のドメイン名
         *               開発環境なら、https://apitest.wakuwakuapp.com/
         *               ステージング環境なら、https://apitest.lw-stg.com/ など・・・
         */
        public boolean checkNetworkUpdate(String domain) {
            registerCheckUpdateDomain(domain);
            return checkUpdate("");
        }

        /**
         * システムイメージファイルのダウンロードと、システムアップデートを一括で実行開始する。
         * ネットワーク経由でアップデートのイメージファイルダウンロードから、ダウンロードしたファイルの
         * MD5によるチェック、その後システムアップデート実行し、全て完了したらデバイスの再起動までを
         * 一括して行う。
         *
         * このAPIはすぐにリターンされ、上記処理はバックグランドで実行される。
         * イメージファイルのダウンロードが始まると、Callback.notifyDownloadProgress()でダウンロードの
         * 途中経過が通知される。
         * ダウンロード完了すると、Callback.onFinishDownload()でダウンロードの結果が通知され、そのまま
         * システムアップデートのインストールが開始される。
         * アップデートの途中経過は、Callback.notifySystemUpdateProgress()で通知される。
         * 処理完了は、Callback.onFinishSystemUpdate()で結果が通知されるが、全ての処理が正常に完了した
         * 場合は、そのままデバイスが自動的に再起動される。
         *
         * checkNetworkUpdate()が実行されて、アップデートが有りの場合以外は false を返して終了する。
         *
         * @return true：リクエスト実行できた。trueの場合は、あとで Callback.onFinishSystemUpdate() が
         *               呼び出されて、アップデートの結果が通知される。
         *         false：リクエスト実行できなかった。この場合は、コールバック呼び出しされない。
         */
        public boolean startNetworkUpdate() {
            return downloadAndApplyUpdate();
        }

        /**
         * 実行中のシステムアップデートをキャンセルする。
         *
         * キャンセル可能なのは、イメージファイルダウンロード中のみで、ダウンロード完了してインストールが
         * 既に開始されている場合は false を返して何もせずに終了する。
         * また、アップデート処理が何も動いていない状態（キャンセルするものが何もない状態）の場合も false を
         * 返して何もせずに終了する。
         *
         * @return true：キャンセル受付された。あとで Callback.onFinishDownload() が呼び出される。
         *               その際にパラメータとして、キャンセルによるダウンロードエラーが通知される。
         *         false：キャンセルできなかった。この場合は、コールバック呼び出しされない。
         */
        public boolean cancelNetworkUpdate() {
            return cancel();
        }

        /**
         * ストレージ内のアップデートイメージファイルからシステムアップデート実行する。
         * ネットワークからのアップデートイメージのダウンロードを行わずに、デバイス内の特定のパスに
         * 格納されているイメージファイルを使ってアップデートを実行する。
         *
         * ＜使い方＞
         * 以下のパスにTC社から提供されるアップデート用のzipファイルを置いた状態で、このAPIをコールする。
         * /storage/emulated/0/Android/data/jp.co.westunitis.systemupdater/files/SystemImg
         *
         * このAPIが呼ばれると、上記の場所にあるアップデートイメージファイルが正しいかどうかを確認して
         * 正しいファイルだった場合のみアップデートが実行される。
         * ファイルが存在しない場合や正常と判断されない場合は false を返して終了する。
         * アップデートの途中経過は、Callback.notifySystemUpdateProgress()で通知される。
         * 処理完了は、Callback.onFinishSystemUpdate()で結果が通知されるが、全ての処理が正常に完了した
         * 場合は、そのままデバイスが自動的に再起動される。
         *
         * @return true：リクエスト実行できた。trueの場合は、あとで Callback.onFinishSystemUpdate() が
         *               呼び出されて、アップデートの結果が通知される。
         *         false：リクエスト実行できなかった。この場合は、コールバック呼び出しされない。
         */
        public boolean applyLocalUpdate() {
            return false;
        }

        /**
         * 現在のアップデートステータスを取得する。
         *
         * @return int 下記のいずれかを返却する（同期）
         *             アイドル状態：{@code STATUS_IDLE} = 0;
         *             エラー状態：{@code STATUS_ERROR} = 1;
         *             アップデート確認中：{@code STATUS_CHECKING_UPDATE} = 2;
         *             アップデート待ち状態：{@code STATUS_UPDATE_AVAILABLE} = 3;
         *             ダウンロード中：{@code STATUS_DOWNLOADING} = 4;
         *             ダウンロードキャンセル要求中：{@code STATUS_CANCEL_DOWNLOADING} = 5;
         *             アップデートインストール中：{@code STATUS_APPLYING_UPDATE} = 6;
         */
        public int getStatus() {
            return getState();
        }
    }

    private Context mContext;
    private Callback mCallback;
    private IWuSystemUpdater mSystemUpdater;
    private final IWuSystemUpdaterListener.Stub mStub = new IWuSystemUpdaterListener.Stub() {
        @Override
        public void onResponseCheckUpdate(boolean result, boolean available, int downloadSize,
                                          Bundle bundle)
                throws RemoteException {
            Log.d(TAG, "onResponseCheckUpdate: result=" + result +
                    " available=" + available + " downloadSize=" + downloadSize);

            if (bundle != null) {
                bundle.setClassLoader(getClass().getClassLoader());
                Parcelable[] parcelables = bundle.getParcelableArray("system_update_info");

                WuSystemUpdateInfo[] infos = new WuSystemUpdateInfo[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    infos[i] = (WuSystemUpdateInfo) parcelables[i];
                }

                WuSystemUpdater.this.onResponseCheckUpdate(result, available, downloadSize, infos);
            } else {
                WuSystemUpdater.this.onResponseCheckUpdate(result, available, downloadSize,
                        null);
            }
        }

        @Override
        public void notifyDownloadProgress(int downloaded, int total) throws RemoteException {
            Log.d(TAG, "onProgressDownload: downloaded=" + downloaded + " / " + total);
            WuSystemUpdater.this.notifyDownloadProgress(downloaded, total);
        }

        @Override
        public void onFinishDownload(boolean result, int downloadSize, int reason)
                throws RemoteException {
            Log.d(TAG, "onFinishDownload: result=" + result +
                    " downloadSize=" + downloadSize + " reason=" + reason);
            WuSystemUpdater.this.onFinishDownload(result, downloadSize, reason);
        }

        @Override
        public void notifySystemUpdateProgress(double progress) throws RemoteException {
            Log.d(TAG, "onProgressSystemUpdate: progress=" + progress);
            WuSystemUpdater.this.notifySystemUpdateProgress(progress);
        }

        @Override
        public void onFinishSystemUpdate(boolean result, int reason) throws RemoteException {
            Log.d(TAG, "onResponseSystemUpdate: result=" + result + " reason=" + reason);
            WuSystemUpdater.this.onFinishSystemUpdate(result, reason);
        }

        @Override
        public void onChangeStatus(int status) throws RemoteException {
            Log.d(TAG, "onChangeStatus: status=" + status);
            WuSystemUpdater.this.onChangeStatus(status);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: ComponentName=" + name.toString());
            mSystemUpdater = IWuSystemUpdater.Stub.asInterface(service);
            try {
                mBoundUpdaterService = true;
                mSystemUpdater.registerListener(mStub);
                WuSystemUpdater.this.onServiceConnected();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: Service disconnected!!!! ComponentName=" +
                    name.toString());
            mSystemUpdater = null;
            WuSystemUpdater.this.onServiceDisconnected();
        }
    };

    private void onServiceConnected() {
        if (mCallback != null) {
            mCallback.onServiceConnected(new Manager());
        }
    }

    private void onServiceDisconnected() {
        if (mCallback != null) {
            mCallback.onServiceDisconnected();
        }
    }

    private void onResponseCheckUpdate(boolean result, boolean available, int size,
                                       WuSystemUpdateInfo[] infos) {
        if (mCallback != null) {
            mCallback.onFinishCheckUpdate(result, available, size, infos);
        }
    }

    private void notifyDownloadProgress(int downloaded, int total) {
        if (mCallback != null) {
            mCallback.notifyDownloadProgress(downloaded, total);
        }
    }

    private void onFinishDownload(boolean result, int downloadSize, int reason) {
        if (mCallback != null) {
            mCallback.onFinishDownload(result, downloadSize, reason);
        }
    }

    private void notifySystemUpdateProgress(double progress) {
        if (mCallback != null) {
            mCallback.notifySystemUpdateProgress(progress);
        }
    }

    private void onFinishSystemUpdate(boolean result, int reason) {
        if (mCallback != null) {
            mCallback.onFinishSystemUpdate(result, reason);
        }
    }

    private void onChangeStatus(int status) {
        if (mCallback != null) {
            mCallback.onChangeStatus(status);
        }
    }

    private boolean bind() {
        Intent service = new Intent(
                "jp.co.westunitis.systemupdater.WuSystemUpdaterService.ACTION_BIND");
        service.setPackage("jp.co.westunitis.systemupdater");
        if (!mContext.bindService(service, mServiceConnection, BIND_AUTO_CREATE)) {
            Log.d(TAG, "!!!!!!!!bindService returned false!!!!!!!!");
            return false;
        }

        return true;
    }

    public void registerCallback(@NonNull Callback callback) {
        Log.d(TAG, "registerListener()");
        mCallback = callback;
    }

    public void unregisterCallback(@NonNull Callback callback) {
        Log.d(TAG, "unregisterListener()");
        if (callback == mCallback) {
            Log.d(TAG, "unregisterListener() callback is unregistered");
            mCallback = null;
        }
    }

    private void registerCheckUpdateDomain(String domain) {
        Log.d(TAG, "registerCheckUpdateDomain: domain=" + domain);
        if (mBoundUpdaterService == false || mSystemUpdater == null) {
            Log.d(TAG, "registerCheckUpdateDomain: mSystemUpdater is not ready!!!");
            return;
        }
        try {
            mSystemUpdater.registerCheckUpdateDomain(domain);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private boolean checkUpdate(String version) {
        Log.d(TAG, "checkUpdate: version=" + version);
        if (mBoundUpdaterService == false || mSystemUpdater == null) {
            Log.d(TAG, "checkUpdate: mSystemUpdater is not ready!!!");
            return false;
        }
        try {
            return mSystemUpdater.checkUpdate(version);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean downloadAndApplyUpdate() {
        Log.d(TAG, "downloadAndApplyUpdate()");
        if (mBoundUpdaterService == false || mSystemUpdater == null) {
            Log.d(TAG, "downloadAndApplyUpdate: mSystemUpdater is not ready!!!");
            return false;
        }
        try {
            return mSystemUpdater.downloadAndApplyUpdate();
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean cancel() {
        Log.d(TAG, "cancel()");
        if (mBoundUpdaterService == false || mSystemUpdater == null) {
            Log.d(TAG, "cancel: mSystemUpdater is not ready!!!");
            return false;
        }
        try {
            return mSystemUpdater.cancel();
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    private int getState() {
        Log.d(TAG, "getStatus()");
        if (mBoundUpdaterService == false || mSystemUpdater == null) {
            Log.d(TAG, "cancel: mSystemUpdater is not ready!!!");
            return STATUS_INVALID;
        }
        try {
            return mSystemUpdater.getStatus();
        } catch (RemoteException e) {
            e.printStackTrace();
            return STATUS_INVALID;
        }
    }
}
