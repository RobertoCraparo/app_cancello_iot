package com.example.cancello_iot.api;

import com.example.cancello_iot.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class ApiClient {
    private static OkHttpClient client;

    public static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request req = chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .header("Content-Type", "application/json")
                                .header("X-App-Token", BuildConfig.API_TOKEN)
                                .build();
                        return chain.proceed(req);
                    })
                    .build();
        }
        return client;
    }

    public static String baseUrl() { return BuildConfig.SERVER_URL; }
}
