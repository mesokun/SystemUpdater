package jp.co.westunitis.systemupdater.services.http;

import jp.co.westunitis.systemupdater.services.models.resonses.CheckSystemUpdateResponse;
import jp.co.westunitis.systemupdater.services.models.resonses.RequestOnetimeApiTokenResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface IRequestApiEndpoint {
    @GET("/api/v2/updater/token")
    Call<RequestOnetimeApiTokenResponse> requestOnetimeApiToken(@Query("serial") String serial);

    @GET("/api/v2/device/updates")
    Call<CheckSystemUpdateResponse> checkSystemUpdate(@Query("build_code") String buildCode);

    @Streaming
    @GET("/api/v2/device/{build_code}")
    Call<ResponseBody> downloadSystemImg(@Path("build_code") String buildCode);
}
