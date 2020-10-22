package xmodemburner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Objects;

import micael.xmodemBurner.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener, AdapterView.OnItemSelectedListener{

    private enum Connected {False, Pending, True}

    private int deviceId, portNum, baudRate;
    private String newline = "";

    private TextView receiveText;

    private UsbSerialPort usbSerialPort;

    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;
    private BroadcastReceiver broadcastReceiver;
    public ControlLines controlLines;
    private boolean hasStarted = false;
    private boolean xmodem;
    DataInputStream dataInputStream;
    private boolean isAck = false;
    boolean isNack = false;
    int packets = 0;
    byte blocknumber = 0x01;
    int cc = 0;
    int nbytes;
    boolean await = true;
    boolean cancel = false;
    CRC16 c = new CRC16();
    private ProgressBar pgsBar;
    private TextView pText;
    ProgressDialog progress;
    TextView username,quota,file;
    Spinner spinnerFile;
    private int i = 0;
    private byte[] myData = {};
    public final String baseUrl = "https://files.micaelil.com/";



    public void addModule(String moduleId) {
        OkHttpClient client = new OkHttpClient();

        String url = baseUrl  + "add_module" + "/" + moduleId + "/" + file.getText().toString();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });


    }


    public void dec_quota() {
        OkHttpClient client = new OkHttpClient();

        String url = baseUrl  + "dec_quota" + "/" + getUsername() + "/" + spinnerFile.getSelectedItem().toString();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });


    }


    public void sendRequest(String route) {
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setTitle("Loading");
        progress.setMessage("Logging in " + getUsername() + "... ");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();
        OkHttpClient client = new OkHttpClient();
        String url = baseUrl + route;
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username",getUsername())
                .addFormDataPart("password",getPassword())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    alert("Error","Something Went Wrong.\nPlease Make sure that you are connected to the internet");
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if(response.isSuccessful()) {
                    String myresponse = response.body().string();
                    try {
                        JSONArray all = new JSONArray(myresponse);
                        String[] allfiles = new String[all.length()];
                        for(int i=0; i< all.length(); i ++){
                            JSONObject jsonObject = all.getJSONObject(i);
                            allfiles[i] = jsonObject.getString("file").substring(6);
                        }
                        SharedPreferences prefs = Objects.requireNonNull(getActivity()).getSharedPreferences("DATA", MODE_PRIVATE);
                        int index = prefs.getInt("index", 0);

                        JSONObject res = all.getJSONObject(index);

                        String quota = res.getString("quota");
                        String files = res.getString("file");
                        String endDate = res.getString("end_date");
                        String numOfDays = "Always";
                        if(endDate.equals("null") || endDate.equals("") ) {
                        numOfDays = "Always";
                        }
                        else {
                            Date userDob = new SimpleDateFormat("yyyy-MM-dd").parse(endDate);
                            Date today = new Date();
                            long diff =  today.getTime() - userDob.getTime();
                             numOfDays = "" + (int) (diff / (1000 * 60 * 60 * 24));
                        }


                        String finalNumOfDays = numOfDays;
                        getActivity().runOnUiThread(() -> {
                            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item, allfiles);
                            spinnerFile.setAdapter(adapter);
                            spinnerFile.setSelection(index);
                            TerminalFragment.this.quota.setText("Quota: " + quota);
                            TerminalFragment.this.username.setText("Welcome " + getUsername());
                            TerminalFragment.this.file.setText(files.substring(6));
                            downloadFile((String) TerminalFragment.this.file.getText());
                            progress.dismiss();
                            if(Integer.parseInt(quota) <= 0 )
                                alert("Error", "Quota has been finished");
                            else
                            controlLines.startAutomation();
                        });

                    } catch (JSONException | ParseException e) {
                        getActivity().runOnUiThread(() -> {
                            progress.dismiss();
                            alert("Error", "Something went wrong", true);
                        });

                    }


                }
                else {
                    String errorBodyString = response.body().string();
                    getActivity().runOnUiThread(() -> {
                        progress.dismiss();
                        alert("Error","It seems that you don't have access anymore, contact the administrator to regain access",false);

                    });
                    Log.d("POOP",errorBodyString);
                }

            }

        });
    }

    public void alert(String title, String message){
        new androidx.appcompat.app.AlertDialog.Builder(Objects.requireNonNull(getActivity()))
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .show();

    }

    public void alert(String title, String message, boolean cancel){
        new androidx.appcompat.app.AlertDialog.Builder(Objects.requireNonNull(getActivity()))
                .setTitle(title)
                .setMessage(message)
                .setCancelable(cancel)
                .show();
    }


    public void downloadFile(String file) {
        File myfile = new File(Objects.requireNonNull(getActivity()).getExternalFilesDir("")+"/File", file);
        if(myfile.exists()) return;
        String secret = "bWljYWVsOmVub2RtaWx2YWRv";
        String DownloadUrl = "https://switch.micaelil.com/" + file;
        DownloadManager.Request request1 = new DownloadManager.Request(Uri.parse(DownloadUrl));
        request1.setDescription("module binary file");   //appears the same in Notification bar while downloading
        request1.setTitle("AP_SL_Boot.bin");
        request1.setVisibleInDownloadsUi(false);

        request1.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI
                        | DownloadManager.Request.NETWORK_MOBILE);

        request1.allowScanningByMediaScanner();
        request1.addRequestHeader("Authorization", "Basic " + secret);
        request1.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        request1.setDestinationInExternalFilesDir(getContext(), "/File", file);

        @SuppressLint("UseRequireInsteadOfGet") DownloadManager manager1 = (DownloadManager) Objects.requireNonNull(getContext()).getSystemService(Context.DOWNLOAD_SERVICE);
        Objects.requireNonNull(manager1).enqueue(request1);
        if (DownloadManager.STATUS_SUCCESSFUL == 8) {
            //   DownloadSuccess();
            //  alert("Yay","yy");
        }
        else{
            //  DownloadFail();
            //   alert("Bad","hh");
        }

    }




    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Constants.INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        //shared prefrences
        if(getUsername().equals("") || getPassword().equals("")){
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
        }

    }

    public String getUsername(){
        SharedPreferences prefs = Objects.requireNonNull(getActivity()).getSharedPreferences("DATA", MODE_PRIVATE);
        return prefs.getString("username", "");//"" is the default value.
    }

    public String getPassword(){
        SharedPreferences prefs = Objects.requireNonNull(getActivity()).getSharedPreferences("DATA", MODE_PRIVATE);
        return prefs.getString("password", "");//"" is the default value.
    }


    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
        if (controlLines != null && connected == Connected.True)
            controlLines.start();
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        if (controlLines != null)
            controlLines.stop();
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString(), null));
        controlLines = new ControlLines(view);
        file = (TextView)  view.findViewById(R.id.file);
        spinnerFile = (Spinner) view.findViewById(R.id.spinnerFile);
        spinnerFile.setOnItemSelectedListener(this);


        quota = (TextView)  view.findViewById(R.id.quota);
        username = (TextView)  view.findViewById(R.id.username);

        Button start =view.findViewById(R.id.start);
        start.setOnClickListener(b -> {
            controlLines.startAutomation();
        });





        sendRequest("login/");
        // alert("Welcome","Welcome Micael lets start");
        // downloadFile();
        pgsBar = (ProgressBar) view.findViewById(R.id.pBar);
        pText = (TextView) view.findViewById(R.id.pText);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = {"Automatic (Dangerous)", (String) file.getText()};
            String[] newlineValues =  {"Automtaic (Dangerous)",(String) file.getText()};
            int pos = Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Files");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();

            return true;
        } else {
            alert("Change","FIle change");
            return super.onOptionsItemSelected(item);
        }


    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    public void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        controlLines.stop();
        service.disconnect();
        usbSerialPort = null;
    }

    private void send(String str, byte[] bytess) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "bad connected", Toast.LENGTH_SHORT).show();
            isNack = true;
            return;
        }
        try {
            if (str.equals(""))
                str = new String(bytess);

            // str="b";
            SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            getActivity().runOnUiThread(() ->{
                receiveText.append(spn);
            });
            byte[] data = bytess;
            if (!str.equals(""))
                data = (str).getBytes();
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    public void sendBytes(byte[] data) {
        try {
            service.write(data);
        } catch (IOException e) {
            status(e.getMessage());
        }

    }

    private void receive(byte[] data) {
        //
        this.packets++;
        //

        try {
            xmodemHandler(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        getActivity().runOnUiThread(() ->{

            receiveText.append(new String(data));
            receiveText.append("\n");
        });
    }

    public void xmodemHandler(byte[] data) throws IOException {
        AssetManager assetManager = Objects.requireNonNull(this.getContext()).getAssets();
        //OPEN FILE
        File myfile = new File(Objects.requireNonNull(getActivity()).getExternalFilesDir("")+"/File", spinnerFile.getSelectedItem().toString());
        pgsBar.setMax((int) (Math.round(myfile.length()/128.00) + 2));



        // number of bytes read into the buffer


        byte[] sector = new byte[Xmodem.sector_size];
        byte[] packet = new byte[Xmodem.packet_size];
        boolean flag = true;
        cancel = false;
        isNack = false;
        this.cc = 0;




        if (data[0] == Xmodem.C) {
            //   Toast.makeText(getContext(), "C", Toast.LENGTH_SHORT).show();
            FileInputStream stream = new FileInputStream(myfile);
            //   dataInputStream = new DataInputStream(assetManager.open("AP_SL_Boot.bin"));
            dataInputStream = new DataInputStream(stream);


            nbytes = dataInputStream.read(sector);


            // If the last packet is less than 128 bytes, fill it with 0xff
            if (nbytes < Xmodem.sector_size) {
                for (int i = nbytes; i < Xmodem.sector_size; i++) {
                    sector[i] = (byte) 0xff;
                }
            }

            sendPacket(blocknumber,sector);
            // increment package
            this.cc++;





        } else if (doesContain(data, Xmodem.NAK)) {
            isNack = true;
            Toast.makeText(getContext(), "nack", Toast.LENGTH_SHORT).show();
        } else if (doesContain(data, Xmodem.ACK)) {
            isAck = true;
            if((nbytes=dataInputStream.read (sector))> 0 ) {
                status("ACKnoledged");

                pgsBar.setProgress(packets);
                pText.setText(packets+" / " + pgsBar.getMax());
                status(packets + " ");
                //  nbytes = dataInputStream.read(sector);


                // If the last packet is less than 128 bytes, fill it with 0xff
                if (nbytes < Xmodem.sector_size) {
                    for (int i = nbytes; i < Xmodem.sector_size; i++) {
                        sector[i] = (byte) 0xff;
                    }
                }

                sendPacket((byte) ((++blocknumber) % 256), sector);
                // increment package

                this.cc++;
            }
            else{
                sendEnd();
                send("l", null);
                send("b", null);
                pgsBar.getProgressDrawable().setColorFilter(ContextCompat.getColor(getContext(), R.color.green), PorterDuff.Mode.SRC_IN );
                MediaPlayer ring = MediaPlayer.create(getActivity(), R.raw.yes);
                ring.start();
                // decrease quota
                dec_quota();
                controlLines.startAutomation();
            }


        } else if (doesContain(data, Xmodem.CAN)) {
            cancel = true;
            status("Recived Cancel key");
        } else if (new String(data).contains("Chip")) {

            // Get Chip

            addModule(new String(data));

            send("u", null);
            hasStarted = true;
            MediaPlayer ring = MediaPlayer.create(getActivity(), R.raw.beep);
            ring.start();
            progress.dismiss();
        }
    }



    public boolean doesContain(byte[] data, byte b) {
        for (byte d : data) {
            if (b == d)
                return true;
        }
        return false;
    }

    public void begin() {
        AssetManager assetManager = this.getContext().getAssets();
        // number of bytes read into the buffer
        int nbytes;
        byte blocknumber = 0x01;
        byte[] sector = new byte[Xmodem.sector_size];
        byte[] packet = new byte[Xmodem.packet_size];
        boolean flag = true;
        cancel = false;
        isNack = false;
        this.cc = 0;
        this.packets = 0;
        Handler handler = new Handler();
        try {
            DataInputStream dataInputStream = new DataInputStream(assetManager.open("AP_SL_Boot.bin"));
            nbytes = dataInputStream.read(sector);

            // If the last packet is less than 128 bytes, fill it with 0xff
            if (nbytes < Xmodem.sector_size) {
                for (int i = nbytes; i < Xmodem.sector_size; i++) {
                    sector[i] = (byte) 0xff;
                }
            }

            sendPacket(blocknumber,sector);
            // increment package
            this.cc++;



            blocknumber = (byte) ((++blocknumber) % 256);
            nbytes = dataInputStream.read(sector);

            // If the last packet is less than 128 bytes, fill it with 0xff
            if (nbytes < Xmodem.sector_size) {
                for (int i = nbytes; i < Xmodem.sector_size; i++) {
                    sector[i] = (byte) 0xff;
                }
            }

            sendPacket(blocknumber,sector);
            // increment package
            this.cc++;


            // After all data is sent, Send end flag
            //while (!isAck) {


            handler.postDelayed(() -> {

                byte[] EOT = new byte[1];
                EOT[0] = Xmodem.EOT;
                sendBytes(EOT);
                sendBytes(EOT);
                sendBytes(EOT);
                send("l", null);
                send("b", null);


                if (isNack || packets < 300) {
                    MediaPlayer ring = MediaPlayer.create(getActivity(), R.raw.error);
                    ring.start();

                } else {
                    MediaPlayer ring = MediaPlayer.create(getActivity(), R.raw.yes);
                    ring.start();
                    status("cc: " + cc + ", packets: " + packets);
                }

                hasStarted = false;

            }, 3000);


            //  isack=getdata () == ack;
            //     }
        } catch (Exception e) {
            status(Arrays.toString(e.getStackTrace()));
        }
    }

    public void sendPacket(byte blocknumber, byte[] sector){

        byte[] packet = new byte[Xmodem.packet_size];
        packet[0] = Xmodem.SOH;
        packet[1] = blocknumber;
        packet[2] = (byte) ~blocknumber;
        for (int i = 3; i <= 130; i++) {
            packet[i] = sector[i - 3];
        }
        CRC16 crc = new CRC16();
        crc.update(sector);
        byte[] crcbytes = crc.getCRCBytes();
        packet[131] = crcbytes[2];
        packet[132] = crcbytes[3];
        sendBytes(packet);
    }

    public void sendEnd(){
        byte[] EOT = new byte[1];
        EOT[0] = Xmodem.EOT;
        sendBytes(EOT);
    }


    public void status(String str) {
        this.getActivity().runOnUiThread(() -> {

            SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);

        });
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        controlLines.start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        if (hasStarted) {
            MediaPlayer error = MediaPlayer.create(getActivity(), R.raw.error);
            error.start();
        }
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    public class ControlLines {
        private static final int refreshInterval = 200; // msec

        private Handler mainLooper;
        private Runnable runnable;
        private ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn;
        private Button xmodem, riBtn;

        ControlLines(View view) {
            mainLooper = new Handler(Looper.getMainLooper());
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            riBtn.setOnClickListener(view1 -> {
                dtrBtn.setChecked(false);
                toggle(dtrBtn);
                startAutomation(); //START
            });

            dtrBtn.setOnClickListener(this::toggle);
            rtsBtn.setOnClickListener(this::toggle);
            xmodem = view.findViewById(R.id.xmodem);
            xmodem.setOnClickListener(view1 -> {
                StartXmodem();

            });
            //  if (connected != Connected.True)
            //  startAutomation();

        }


        public void toggleDTR() {
            dtrBtn.setChecked(false);
            toggle(dtrBtn);
            dtrBtn.setChecked(true);
            toggle(dtrBtn);
        }

        public void startAutomation() {

            progress = new ProgressDialog(getContext());
            progress.setTitle("Initializing");
            progress.setMessage("Pinging Dongle ...");
            progress.setCancelable(true); // disable dismiss by tapping outside of the dialog
            progress.show();
            //  Toast.makeText(getContext(), "Automate", Toast.LENGTH_LONG).show();
            hasStarted = false;
            blocknumber = 0x01;
            isAck = false;
            packets = 0;
            Objects.requireNonNull(getActivity()).runOnUiThread(() ->{
                Handler checker = new Handler();

                final int[] temp = {packets};
                checker.postDelayed(new Runnable() {

                    public void run() {
                        if(temp[0] == packets && isAck){
                            MediaPlayer ring = MediaPlayer.create(getActivity(), R.raw.error);
                            pgsBar.getProgressDrawable().setColorFilter(ContextCompat.getColor(getContext(), R.color.red), PorterDuff.Mode.SRC_IN );


                            ring.start();
                            isAck = false;
                            checker.removeCallbacks(runnable);
                            startAutomation();
                        }
                        temp[0] = packets;
                        checker.postDelayed(this,2000);
                    }},2000);
            });
            // set to 1 for two seconds
            // set to 0 for 200 ms
            // set to 1
            // send u
            // if get response send another u and start xmodem
            // else again
            Handler handler = new Handler();
            Handler response = new Handler();
            Handler startXmodem = new Handler();
            int delay = 2000; //milliseconds



            handler.postDelayed(new Runnable() {
                boolean flag = false;
                int i = 0;

                public void run() {
                    //do something
                    //    status(String.valueOf(i));
                    if (hasStarted || isNack || connected != Connected.True) {
                        handler.removeCallbacks(runnable);
                    } else {
                        toggleDTR();

                        // send u

                        response.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                send("u", null);

                            }
                        }, 500);


                        handler.postDelayed(this, delay);
                    }
                }
            }, delay);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (connected != Connected.True) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) {
                    ctrl = "RTS";
                    usbSerialPort.setRTS(btn.isChecked());
                }
                if (btn.equals(dtrBtn)) {
                    ctrl = "DTR";
                    //  Toast.makeText(getContext(), "Changed to " + (btn.isChecked() ? "1" : "0"), Toast.LENGTH_LONG).show();
                    usbSerialPort.setDTR(!btn.isChecked());
                }
            } catch (IOException e) {
                status("set" + ctrl + " failed: " + e.getMessage());
            }
        }

        private void run() {
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                //riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS))
                    rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS))
                    ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR))
                    dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR))
                    dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))
                    cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))  // riBtn.setVisibility(View.INVISIBLE);
                    run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }


        }

        void stop() {
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(true);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            //  riBtn.setChecked(false);
        }
    }

    public void StartXmodem() {
      /*  send("u",null);
        send("u",null);*/


        begin();


    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        SharedPreferences.Editor editor = getActivity().getSharedPreferences("DATA", MODE_PRIVATE).edit();
        editor.putInt("index",position);
        editor.apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}


