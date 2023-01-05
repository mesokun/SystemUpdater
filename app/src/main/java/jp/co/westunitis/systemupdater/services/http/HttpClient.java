package jp.co.westunitis.systemupdater.services.http;

import android.annotation.SuppressLint;
import android.os.Build;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import jp.co.westunitis.systemupdater.BuildConfig;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HttpClient {
    private final String TAG = "HttpClient";
    private static final String URL = "https://apitest.wakuwakuapp.com/";
    private static final String KEY = "9eFeg2hhkQTctNMkTWcyzpPTjxubdSNMAwd7EhaUbLTswnrSC3GHBA2UgrBL3nCA";

    private static Interceptor mInterceptor = new Interceptor() {
        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("X-Client-Key", KEY)
                    .method(original.method(), original.body())
                    .build();
            Response response = chain.proceed(request);
            return response;
        }
    };
    private static final OkHttpClient mClient = new OkHttpClient.Builder()
            .addInterceptor(mInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final Gson mGson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public static IRequestApiEndpoint mEndpoint;

    public static IRequestApiEndpoint getEndpoint() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(URL)
                .addConverterFactory(GsonConverterFactory.create(mGson))
                .client(mClient)
                .build();
        mEndpoint = retrofit.create(IRequestApiEndpoint.class);
        return mEndpoint;
    }

    public static IRequestApiEndpoint getEndpoint(String endpointUrl) {
        if (null != endpointUrl) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(endpointUrl)
                    .addConverterFactory(GsonConverterFactory.create(mGson))
                    .client(mClient)
                    .build();
            mEndpoint = retrofit.create(IRequestApiEndpoint.class);
            return mEndpoint;
        } else {
            return null;
        }
    }

    public static OkHttpClient getClient(final String key) {
        Interceptor interceptor = new Interceptor() {
            @NotNull
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("X-Client-Key", key)
                        .header("User-Agent", getUserAgent(key))
                        .method(original.method(), original.body())
                        .build();
                Response response = chain.proceed(request);
                return response;
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        return client;
    }

    public static OkHttpClient getClient(final String key, final String token) {
        Interceptor interceptor = new Interceptor() {
            @NotNull
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("X-Client-Key", key)
                        .header("X-Client-Token", token)
                        .header("User-Agent", getUserAgent(key))
                        .method(original.method(), original.body())
                        .build();
                Response response = chain.proceed(request);
                return response;
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        return client;
    }

    public static IRequestApiEndpoint getEndpoint(String endpointUrl, OkHttpClient client) {
        if (null != endpointUrl || null != client) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(endpointUrl)
                    .addConverterFactory(GsonConverterFactory.create(mGson))
                    .client(client)
                    .build();
            mEndpoint = retrofit.create(IRequestApiEndpoint.class);
            return mEndpoint;
        } else {
            return null;
        }
    }

    @SuppressLint("DefaultLocale")
    private static String getUserAgent(String uuid) {
        return String.format("AppUpdater/%s/(Android;%d;%s;%s-%s;%s)",
                BuildConfig.VERSION_NAME,
                Build.VERSION.SDK_INT,
                Build.MODEL,
                Locale.getDefault().getLanguage(),
                Locale.getDefault().getCountry(),
                uuid);
    }
}
