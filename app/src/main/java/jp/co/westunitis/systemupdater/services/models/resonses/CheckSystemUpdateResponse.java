package jp.co.westunitis.systemupdater.services.models.resonses;

import com.google.gson.annotations.SerializedName;

public class CheckSystemUpdateResponse {
    @SerializedName("updates")
    public Updates mUpdates[];

    public static class Updates {
        @SerializedName("build_code")
        public String mBuildCode;

        @SerializedName("note")
        public String mNote;

        @SerializedName("size")
        public int mSize;

        @SerializedName("hash")
        public String mHash;

        @SerializedName("full_updated")
        public boolean mFullUpdated;

        @SerializedName("url")
        public String mUrl;
    }
}
