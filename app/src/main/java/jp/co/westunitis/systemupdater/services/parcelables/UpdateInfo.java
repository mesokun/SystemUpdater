package jp.co.westunitis.systemupdater.services.parcelables;

import android.os.Parcel;
import android.os.Parcelable;

public final class UpdateInfo implements Parcelable {
    public String mBuildCode;
    public String mNote;
    public int mDownloadSize;
    public String mHash;
    public boolean mFullUpdated;

    public static final Parcelable.Creator<UpdateInfo> CREATOR = new Creator<UpdateInfo>() {
        @Override
        public UpdateInfo createFromParcel(Parcel source) {
            return new UpdateInfo(source);
        }

        @Override
        public UpdateInfo[] newArray(int size) {
            return new UpdateInfo[size];
        }
    };

    public UpdateInfo() {
        // constructor
    }

    public UpdateInfo(Parcel source) {
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
