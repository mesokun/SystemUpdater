# SystemUpdater

このアプリは、TCから提供されるシステムFW（Neckband）をアップデートするためのアプリです。

基本的には、AOSPが提供している以下のSystemUpdaterSampleアプリをAndroidStudioでビルドできるように
したものになります。
https://android.googlesource.com/platform/bootable/recovery/+/master/updater_sample/


＜対象実機＞  
・EVT1基盤とワーキングモックにこのアプリをインストールして、TCが提供するアップデート用のバイナリを使うことで  
　A/Bシステムアップデートが実行できることは確認済みです。  
・このアプリはシステムアプリになので、インストールにはroot権限が必要です。  
　AndroidStudioのRunやデバッグ実行、及び、adb installコマンドではインストールできません。  
・普通のスマホにはインストールできません。

＜ビルド＆APK作成＞  
・このアプリはシステムアプリになります。  
・ビルド自体は、このプロジェクトをクローンしてAndroidStudioで普通にビルドできます。  
・APK作成時に、TCから提供されているplatform.keystore（証明書）をつけてビルドする必要があります。  
　証明書は、/app/keystoreディレクトリに入っています。  
・APK作成手順  
　1.AndroidStudioの「Build」->「Generate Signed Bundle/APK」->「APK」  
　2.証明書のIDとパスワードは以下の通り  
     storePassword "123456"  
     keyAlias "wizard"  
     keyPassword "123"  
　3.「debug」->「Full APK Signature」でAPK作成する  

＜インストール＞  
・システムアプリなので、/system/priv-app以下にインストールする必要がある。  
・手順は以下の通り  
　1.アプリをインストールするEVT実機に接続してADBコマンドが使える状態にする  
　2.ADBコマンドで、verity無効化する  
　　　>adb root  
　　　>adb disable-verity  
　　　>adb shell reboot  
　3./systemをリマウントして書き込み可能な状態にする（これしないとAPKをコピーできない）  
　　　>adb root  
　　　>adb remount  
　4.APKをインストールするためのディレクトリを作る  
　　　>adb shellでshellに入って以下のディレクトリを作る  
　　　/system/priv-app/SystemUpdater　<-このディレクトリを作って、この中にAPKをコピーする  
　　　　　　　　　　　　　　　　　　　　　　　ディレクトリの名前はSystemUpdaterじゃなくてもOK  
　5.上記で作ったディレクトリにAPKをコピー  
　　　>adb push xxx.apk /system/priv-app/SystemUpdater  

