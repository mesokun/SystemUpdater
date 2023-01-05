package jp.co.westunitis.systemupdater;

import android.os.Parcel;
import android.os.Parcelable;

public final class WuSystemUpdateInfo implements Parcelable {
    public String mBuildCode;
    public String mNote;
    public int mDownloadSize;
    public String mHash;
    public boolean mFullUpdated;

    public static final Parcelable.Creator<WuSystemUpdateInfo> CREATOR =
            new Parcelable.Creator<WuSystemUpdateInfo>() {
        @Override
        public WuSystemUpdateInfo createFromParcel(Parcel source) {
            return new WuSystemUpdateInfo(source);
        }

        @Override
        public WuSystemUpdateInfo[] newArray(int size) {
            return new WuSystemUpdateInfo[size];
        }
    };

    public WuSystemUpdateInfo() {
        // constructor
    }

    public WuSystemUpdateInfo(Parcel source) {
        // constructor
        readFromParcel(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int i) {
        dest.writeString(mBuildCode);
        dest.writeString(mNote);
        dest.writeInt(mDownloadSize);
        dest.writeString(mHash);
        dest.writeBoolean(mFullUpdated);
    }

    public void readFromParcel(Parcel in) {
        mBuildCode = in.readString();
        mNote = in.readString();
        mDownloadSize = in.readInt();
        mHash = in.readString();
        mFullUpdated = in.readBoolean();
    }
}
