// IWuSystemUpdaterListener.aidl
package jp.co.westunitis.systemupdater;

// Declare any non-default types here with import statements

interface IWuSystemUpdaterListener {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
//    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
//            double aDouble, String aString);

    /**
     * システムアップデートの有無を確認した結果を通知する。
     * アップデートがあった場合には、アップデート有りが通知される。
     *
     * @param result アップデートチェック結果 true:成功、false:失敗
     *               この値が成功（true）のときのみ、以下のパラメータの値は有効とする。
     * @param available アップデートの有無 true:アップデート有、false:なし
     *               複数パッケージの場合は、どれか1つでもアップデートがあればtrueが通知される。
     * @param downloadSize ダウンロードするAPKの合計サイズ
     * @param in Bundle アップデート詳細情報
     *               複数アップデート情報の配列が渡される（アップデート情報が一つの場合は要素数1の配列）
     */
    void onResponseCheckUpdate(boolean result, boolean available, int downloadSize, in Bundle bundle);

    /**
     * ダウンロードの進捗率を0.0から1.0の間で通知する。
     *
     * @param downladed ダウンロード済みのサイズ（Bytes）
     * @param total トータルのサイズ（Bytes）
     */
    void notifyDownloadProgress(int downloaded, int total);

    /**
     * システムアップデートイメージファイルのダウンロード結果を通知する。
     *
     * @param result 結果 true:ダウンロードとストレージ保存成功、false:失敗
     *               失敗時は、falseを通知をして処理終了する
     * @param downloadSize ダウンロードしたファイルのサイズ（byte）
     *                     失敗時は、0を通知する
     * @param reason 処理失敗したときの理由（以下のいずれか）を通知する
     *                0 ダウンロードとストレージ保存成功
     *               -1 ダウンロードが途中キャンセルされた場合
     *               -2 ダウンロードが失敗（ネットワーク接続エラーなど）した場合
     *               -3 ストレージへのファイル書き込みに失敗した場合（容量不足など）
     */
    void onFinishDownload(boolean result, int downloadSize, int reason);

    /**
     * アップデートのインストールの進捗率を0.0から1.0の間で通知する。
     *
     * @param progress 0.0 - 1.0
     */
    void notifySystemUpdateProgress(double progress);

    /**
     * APKのダウンロードが途中で中断した場合や、ダウンロードしたAPKのストレージ書き込み時にエラーが発生した際に
     * 処理の中断理由を通知するために呼び出される。通常、ダウンロードが成功した場合はAPKのインストール完了する
     * まで、処理が中断することなく実行され、全ての処理完了後にシステムが再起動される。その場合は、このリスナー
     * 呼び出しは行われない。
     *
     * @param result システムアップデート結果 true:成功、false:失敗
     *               成功時は、trueを通知をした後にデバイスを再起動する
     *               失敗時は、falseを通知をして処理終了する
     * @param reason 処理失敗したときの理由（以下のいずれか）を通知する
     *                0 システムアップデート成功（この後すぐに再起動）
     *               -1 途中キャンセルされた場合
     *               -2 その他の失敗
     */
    void onFinishSystemUpdate(boolean result, int reason);

    /**
     * アップデートステータス変化通知
     *
     * @param status 下記のいずれかを通知する。
     *               アイドル状態：IDLE = 0;
     *               エラー状態：ERROR = 1;
     *               アップデート確認中：CHECKING_UPDATE = 2;
     *               アップデート待ち状態：UPDATE_AVAILABLE = 3;
     *               ダウンロード中：DOWNLOADING = 4;
     *               ダウンロードキャンセル要求中：CANCEL_DOWNLOADING = 5;
     *               アップデートインストール中：APPLYING_UPDATE = 6;
     */
    void onChangeStatus(int status);
}
