/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.westunitis.systemupdater;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jp.co.westunitis.systemupdater.util.LogUtil;

/**
 * An update description. It will be parsed from JSON, which is intended to
 * be sent from server to the update app, but in this sample app it will be stored on the device.
 */
public class UpdateConfig implements Parcelable {
    private static final String TAG = "UpdateConfig";

    public static final int AB_INSTALL_TYPE_NON_STREAMING = 0;
    public static final int AB_INSTALL_TYPE_STREAMING = 1;

    public static final Creator<UpdateConfig> CREATOR =
            new Creator<UpdateConfig>() {
                @Override
                public UpdateConfig createFromParcel(Parcel source) {
                    return new UpdateConfig(source);
                }

                @Override
                public UpdateConfig[] newArray(int size) {
                    return new UpdateConfig[size];
                }
            };

    /** parse update config from json */
    public static UpdateConfig fromJson(String json) throws JSONException {
        UpdateConfig c = new UpdateConfig();

        JSONObject o = new JSONObject(json);
        c.mName = o.getString("name");
        c.mUrl = o.getString("url");
        switch (o.getString("ab_install_type")) {
            case AB_INSTALL_TYPE_NON_STREAMING_JSON:
                c.mAbInstallType = AB_INSTALL_TYPE_NON_STREAMING;
                break;
            case AB_INSTALL_TYPE_STREAMING_JSON:
                c.mAbInstallType = AB_INSTALL_TYPE_STREAMING;
                break;
            default:
                throw new JSONException("Invalid type, expected either "
                        + "NON_STREAMING or STREAMING, got " + o.getString("ab_install_type"));
        }

        // TODO: parse only for A/B updates when non-A/B is implemented
        JSONObject ab = o.getJSONObject("ab_config");
        boolean forceSwitchSlot = ab.getBoolean("force_switch_slot");
        boolean verifyPayloadMetadata = ab.getBoolean("verify_payload_metadata");
        ArrayList<PackageFile> propertyFiles = new ArrayList<>();
        if (ab.has("property_files")) {
            JSONArray propertyFilesJson = ab.getJSONArray("property_files");
            for (int i = 0; i < propertyFilesJson.length(); i++) {
                JSONObject p = propertyFilesJson.getJSONObject(i);
                propertyFiles.add(new PackageFile(
                        p.getString("filename"),
                        p.getLong("offset"),
                        p.getLong("size")));
            }
        }
        String authorization = ab.optString("authorization", null);
        c.mAbConfig = new AbConfig(
                forceSwitchSlot,
                verifyPayloadMetadata,
                propertyFiles.toArray(new PackageFile[0]),
                authorization);

        c.mRawJson = json;
        return c;
    }

    public static UpdateConfig fromZip(final File file) {
        return loadConfigFromFile(file);
    }

    public static UpdateConfig fromZip(Context context, String buildCode) {
        if (null == context || null == buildCode || buildCode.length() < 1) {
            LogUtil.d(TAG, "fromZip: Parameter error!!!!!!");
            return null;
        }

        final File file = new File(context.getExternalFilesDir(null).toString() +
                File.separator + "/SystemImg/" + buildCode + ".zip");

        return loadConfigFromFile(file);
    }

    private static UpdateConfig loadConfigFromFile(final File file) {
        UpdateConfig c = new UpdateConfig();
        ArrayList<PackageFile> propertyFiles = new ArrayList<>();
        long sizeBytes = -1;
        StringBuilder sb = new StringBuilder();

        if (file.isFile()) {
            sizeBytes = file.length();
            LogUtil.v(TAG, "fromZip() " + file.getPath() + " size :" + sizeBytes);
        } else {
            LogUtil.v(TAG, "fromZip() " + file.getPath() + " does not exist");
            return null;
        }

        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[256];

            if (in.read(buffer) != -1) {
                String str = new String(buffer);
                int startIndex = -1;
                int endOffsetIndex = -1;
                int endSizeIndex = -1;
                int offset = -1;
                int size = -1;

                List<String> propFiles = Arrays.asList(
                        "payload_metadata.bin:",
                        "payload.bin:",
                        "payload_properties.txt:",
                        "care_map.pb:",
                        "compatibility.zip:",
                        "metadata:"
                );

                for (String prop : propFiles) {
                    if (-1 != (startIndex = str.indexOf(prop))) {
                        endOffsetIndex = str.indexOf(":", startIndex + prop.length());
                        if (-1 != endOffsetIndex) {
                            offset = Integer.parseInt(str.substring(startIndex + prop.length(), endOffsetIndex));
                            endSizeIndex = str.indexOf(",", endOffsetIndex + 1);
                            if (-1 != endSizeIndex) {
                                size = Integer.parseInt(str.substring(endOffsetIndex + 1, endSizeIndex));

                                propertyFiles.add(new PackageFile(
                                        prop.substring(0, prop.length() - 1),
                                        offset,
                                        size));

                                LogUtil.d(TAG, "fromZip() " + prop.substring(0, prop.length() - 1) +
                                        ": offset:" + offset + " size:" + size);
                                sb.append(prop.substring(0, prop.length() - 1) + ": offset:" +
                                        offset + " size:" + size + "\n");
                            } else {
                                endSizeIndex = str.indexOf(" ", endOffsetIndex + 1);

                                propertyFiles.add(new PackageFile(
                                        prop.substring(0, prop.length() - 1),
                                        offset,
                                        size));

                                if (-1 != endSizeIndex) {
                                    size = Integer.parseInt(str.substring(endOffsetIndex + 1, endSizeIndex));
                                    LogUtil.d(TAG, "fromZip() " + prop.substring(0, prop.length() - 1) +
                                            ": offset:" + offset + " size:" + size);
                                    sb.append(prop.substring(0, prop.length() - 1) + ": offset:" +
                                            offset + " size:" + size + "\n");
                                }
                            }
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        c.mName = "Config from " + file.getName();
        c.mUrl = "file://" + file.getPath();
        c.mAbInstallType = AB_INSTALL_TYPE_STREAMING;
        c.mAbConfig = new AbConfig(
                true,
                true,
                propertyFiles.toArray(new PackageFile[0]),
                null);
        sb.append("\n" + c.mUrl);
        c.mRawJson = sb.toString();

        return c;
    }

    /**
     * these strings are represent types in JSON config files
     */
    private static final String AB_INSTALL_TYPE_NON_STREAMING_JSON = "NON_STREAMING";
    private static final String AB_INSTALL_TYPE_STREAMING_JSON = "STREAMING";

    /** name will be visible on UI */
    private String mName;

    /** update zip file URI, can be https:// or file:// */
    private String mUrl;

    /** non-streaming (first saves locally) OR streaming (on the fly) */
    private int mAbInstallType;

    /** A/B update configurations */
    private AbConfig mAbConfig;

    private String mRawJson;

    protected UpdateConfig() {
    }

    protected UpdateConfig(Parcel in) {
        this.mName = in.readString();
        this.mUrl = in.readString();
        this.mAbInstallType = in.readInt();
        this.mAbConfig = (AbConfig) in.readSerializable();
        this.mRawJson = in.readString();
    }

    public UpdateConfig(String name, String url, int installType) {
        this.mName = name;
        this.mUrl = url;
        this.mAbInstallType = installType;
    }

    public String getName() {
        return mName;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getRawJson() {
        return mRawJson;
    }

    public int getInstallType() {
        return mAbInstallType;
    }

    public AbConfig getAbConfig() {
        return mAbConfig;
    }

    /**
     * @return File object for given url
     */
    public File getUpdatePackageFile() {
        if (mAbInstallType != AB_INSTALL_TYPE_NON_STREAMING) {
            throw new RuntimeException("Expected non-streaming install type");
        }
        if (!mUrl.startsWith("file://")) {
            throw new RuntimeException("url is expected to start with file://");
        }
        return new File(mUrl.substring(7, mUrl.length()));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mUrl);
        dest.writeInt(mAbInstallType);
        dest.writeSerializable(mAbConfig);
        dest.writeString(mRawJson);
    }

    /**
     * Description of a file in an OTA package zip file.
     */
    public static class PackageFile implements Serializable {

        private static final long serialVersionUID = 31043L;

        /** filename in an archive */
        private String mFilename;

        /** defines beginning of update data in archive */
        private long mOffset;

        /** size of the update data in archive */
        private long mSize;

        public PackageFile(String filename, long offset, long size) {
            this.mFilename = filename;
            this.mOffset = offset;
            this.mSize = size;
        }

        public String getFilename() {
            return mFilename;
        }

        public long getOffset() {
            return mOffset;
        }

        public long getSize() {
            return mSize;
        }
    }

    /**
     * A/B (seamless) update configurations.
     */
    public static class AbConfig implements Serializable {

        private static final long serialVersionUID = 31044L;

        /**
         * if set true device will boot to new slot, otherwise user manually
         * switches slot on the screen.
         */
        private boolean mForceSwitchSlot;

        /**
         * if set true device will boot to new slot, otherwise user manually
         * switches slot on the screen.
         */
        private boolean mVerifyPayloadMetadata;

        /** defines beginning of update data in archive */
        private PackageFile[] mPropertyFiles;

        /**
         * SystemUpdaterSample receives the authorization token from the OTA server, in addition
         * to the package URL. It passes on the info to update_engine, so that the latter can
         * fetch the data from the package server directly with the token.
         */
        private String mAuthorization;

        public AbConfig(
                boolean forceSwitchSlot,
                boolean verifyPayloadMetadata,
                PackageFile[] propertyFiles,
                String authorization) {
            this.mForceSwitchSlot = forceSwitchSlot;
            this.mVerifyPayloadMetadata = verifyPayloadMetadata;
            this.mPropertyFiles = propertyFiles;
            this.mAuthorization = authorization;
        }

        public boolean getForceSwitchSlot() {
            return mForceSwitchSlot;
        }

        public boolean getVerifyPayloadMetadata() {
            return mVerifyPayloadMetadata;
        }

        public PackageFile[] getPropertyFiles() {
            return mPropertyFiles;
        }

        public Optional<String> getAuthorization() {
            return mAuthorization == null ? Optional.empty() : Optional.of(mAuthorization);
        }
    }

}
