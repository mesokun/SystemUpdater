// IWuSystemUpdater.aidl
package jp.co.westunitis.systemupdater;

// Declare any non-default types here with import statements
import jp.co.westunitis.systemupdater.IWuSystemUpdaterListener;

interface IWuSystemUpdater {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
//    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
//            double aDouble, String aString);

    /**
     * リスナー登録
     *
     * @param listener IWuSystemUpdaterListener 登録するリスナー
     * @throws RemoteException
     */
    void registerListener(IWuSystemUpdaterListener listener);

    /**
     * リスナー削除
     *
     * @param listener IWuSystemUpdaterListener 削除するリスナー
     * @throws RemoteException
     */
    void unregisterListener(IWuSystemUpdaterListener listener);

    /**
     * システムアップデートを行うアカウントのドメイン名を登録する。
     * このAPIで登録したドメインに対して、アップデートの有無を問い合わせる。
     * （checkUpdateメソッド実行すると、ここで登録したドメインに対してアップデート有無のチェックを行う）
     * このAPIを複数回呼び出した場合は、最後の呼び出しで渡された内容で登録内容が上書きされる。
     *
     * @param domain アップデートを行うアカウントのドメイン名文字列
     * @throws RemoteException
     */
    void registerCheckUpdateDomain(String domain);

    /**
     * システムアップデート有無の確認を行う。registerCheckUpdateDomainメソッドで登録されたドメインに対して
     * アップデート情報取得のリクエストを行う。ドメインが登録されていない場合は、falseを返して終了する。
     *
     * @return true：リクエスト実行できた。この場合は、あとでIWuAppInstallerListenerインターフェイスの
     *               onResponseCheckUpdateリスナーが呼び出され、アップデート有無が通知される。
     *         false：リクエスト実行できなかった。この場合は、リスナー呼び出しされない。
     * @param version 現在のバージョン情報
     * @throws RemoteException
     */
    boolean checkUpdate(String version);

    /**
     * システムIMGのダウンロードとインストールを実行開始する。APKのダウンロードは、checkUpdateメソッドに
     * よって得られたアップデート有無の情報を元にサーバからHTTPSでAPKファイルをダウンロードし、デバイス内の
     * ストレージ（アプリケーション領域）にファイル保存する。アップデート必要な全てのAPKのダウンロードと、
     * ファイル保存が完了したら、それらのAPKのインストールを実行する。インストールはサイレントで実行され、
     * ユーザーによる確認は必要としない。全てのAPKのインストール完了後、システム再起動する。
     *
     * checkUpdateが未実行、またはアップデート無しの場合はfalseを返して終了する。
     *
     * @return true: ダウンロード開始できた。この場合、ダウンロード及びインストールまで完了した場合は
     *               自動的にシステム再起動する。以下のケースではIWuAppInstallerListenerインター
     *               フェイスのonResponseDownloadApkメソッドが呼び出される。
     *               1.ダウンロードが途中キャンセルされた場合
     *               2.ダウンロードが失敗（ネットワーク接続エラーなど）した場合
     *               3.ストレージへのファイル書き込みに失敗した場合（容量不足など）
     *         false：ダウンロード開始しなかった。この場合は、システム再起動やリスナー呼び出しはされない。
     * @throws RemoteException
     */
    boolean downloadAndApplyUpdate();

    /**
     * 実行中のシステムアップデートをキャンセルする。キャンセルできるものがない場合は何もしない。
     *
     * @return true：リクエスト実行できた。この場合は、あとでIWuAppInstallerListenerインターフェイスの
     *               onResponseCheckUpdateリスナーが呼び出され、アップデート有無が通知される。
     *         false：リクエスト実行できなかった。この場合は、リスナー呼び出しされない。
     * @throws RemoteException
     */
    boolean cancel();

    /**
     * 現在のアップデートステータスを取得する。
     *
     * @return int 下記のいずれかを返却する（同期）
     *             アイドル状態：IDLE = 0;
     *             エラー状態：ERROR = 1;
     *             アップデート確認中：CHECKING_UPDATE = 2;
     *             アップデート待ち状態：UPDATE_AVAILABLE = 3;
     *             ダウンロード中：DOWNLOADING = 4;
     *             ダウンロードキャンセル要求中：CANCEL_DOWNLOADING = 5;
     *             アップデートインストール中：APPLYING_UPDATE = 6;
     * @throws RemoteException
     */
    int getStatus();
}
