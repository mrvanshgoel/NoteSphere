package com.notesphere.app.api;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        // Skip auth for login/register if they use the same client
        String path = originalRequest.url().encodedPath();
        if (path.contains("/api/auth/login") || path.contains("/api/auth/register")) {
            return chain.proceed(originalRequest);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return chain.proceed(originalRequest);
        }

        try {
            // Synchronously get the token. getIdToken(true) forces a refresh if needed.
            // Note: intercept() runs on a background thread, so this is safe.
            Task<GetTokenResult> task = user.getIdToken(true);
            GetTokenResult result = Tasks.await(task);
            String token = result.getToken();

            if (token != null) {
                Request newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
                Response response = chain.proceed(newRequest);
                if (response.code() == 401 || response.code() == 403) {
                    com.notesphere.app.utils.SharedPrefManager.forceSignOut(com.notesphere.app.NoteSphereApplication.getAppContext());
                }
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chain.proceed(originalRequest);
    }
}
