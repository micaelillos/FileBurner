package xmodemburner;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import micael.xmodemBurner.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class LoginActivity extends AppCompatActivity   {
    public final String baseUrl = "https://files.micaelil.com/";


    CardView startButton;
    EditText username;
    EditText password;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener((view -> {
               sendRequest("login/");
        }));



        //shared prefrences
        SharedPreferences prefs = getSharedPreferences("DATA", MODE_PRIVATE);
        String name = prefs.getString("username", "");//"" is the default value.
        String password = prefs.getString("password", "");
        if(!name.equals("") && !password.equals("")){
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        }


    }




    public void alert(String title, String message){
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .show();

    }




    public void sendRequest(String route) {

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Loading");
        progress.setMessage("Logging in " + username.getText() + " ...");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();



        OkHttpClient client = new OkHttpClient();
        String url = baseUrl + route;



        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", String.valueOf(username.getText()))
                .addFormDataPart("password",String.valueOf(password.getText()))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();


            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    LoginActivity.this.runOnUiThread(() -> {
                        progress.dismiss();
                        alert("Error","Something Went Wrong.\nPlease Make sure that you are connected to the internet");
                    });

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if(response.isSuccessful()) {
                        String myresponse = response.body().string();
                        Log.d("POOP",myresponse);
                        try {
                            JSONArray all = new JSONArray(myresponse);
                            JSONObject res = all.getJSONObject(0);
                           String quota = res.getString("quota");
                            String files = res.getString("file");
                            String endDate = res.getString("end_date");
                            LoginActivity.this.runOnUiThread(() -> {
                                // To dismiss the dialog
                                progress.dismiss();
                                Log.d("POOP",quota + "," + files);
                                alert("Success","You have been successfully logged in, from now you will be logged in automatically");

                                SharedPreferences.Editor editor = getSharedPreferences("DATA", MODE_PRIVATE).edit();
                                editor.putString("username", String.valueOf(username.getText()));
                                editor.putString("password", String.valueOf(password.getText()));
                                editor.apply();

                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                intent.putExtra("quota", quota);
                                intent.putExtra("enddate",endDate);
                                startActivity(intent);

                            });

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }


                    }
                    else {
                        String errorBodyString = response.body().string();
                        LoginActivity.this.runOnUiThread(() -> {
                            // To dismiss the dialog
                            progress.dismiss();
                            alert("Error","Username or password incorrect");


                        });
                        Log.d("POOP",errorBodyString);
                    }

                }

            });
    }

}