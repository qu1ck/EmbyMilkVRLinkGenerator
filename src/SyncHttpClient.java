import com.google.gson.Gson;
import com.squareup.okhttp.*;
import mediabrowser.apiinteraction.Response;
import mediabrowser.apiinteraction.http.HttpRequest;
import mediabrowser.apiinteraction.http.IAsyncHttpClient;
import mediabrowser.model.logging.ILogger;

import java.io.IOException;
import java.util.Map;

public class SyncHttpClient implements IAsyncHttpClient {
    private ILogger logger;
    public SyncHttpClient(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public void Send(HttpRequest request, Response<String> response) {
        logger.Debug("request: %s", new Gson().toJson(request));
        OkHttpClient okclient = new OkHttpClient();
        String params = new Gson().toJson(request.getPostData());
        Request req;
        Headers.Builder headers = new Headers.Builder();
        for (Map.Entry<String, String> header: request.getRequestHeaders().entrySet()) {
            headers.add(header.getKey(), header.getValue());
        }
        if (request.getMethod().equals("POST")) {
            req = new Request.Builder()
                    .url(request.getUrl())
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), params))
                    .headers(headers.build())
                    .addHeader("x-emby-authorization",
                            request.getRequestHeaders().getAuthorizationScheme() + " "
                                    + request.getRequestHeaders().getAuthorizationParameter())
                    .build();
        } else if (request.getMethod().equals("GET")) {
            req = new Request.Builder()
                    .url(request.getUrl())
                    .get()
                    .headers(headers.build())
                    .build();
        } else {
            response.onError(new UnsupportedOperationException("Method " + request.getMethod() + " is unsupported"));
            return;
        }

        try {
            com.squareup.okhttp.Response resp = okclient.newCall(req).execute();
            logger.Debug("Response %s", resp.code() + " " + resp.message());
            String respString = resp.body().string();
            logger.Debug("Response body: %s", respString + "\n");
            if (resp.code()>300) {
                response.onError(new Exception(resp.code() + " " + resp.message()));
            } else {
                response.onResponse(respString);
            }
        } catch (IOException e) {
            e.printStackTrace();
            response.onError(e);
        }
    }
}
