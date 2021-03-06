package com.example.smarthelmet;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.SEND_SMS;
import static android.content.ContentValues.TAG;

public class Landing extends AppCompatActivity implements LocationListener {

    String address = null, name = null;

    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final int PICK_CONTACT = 1;
    public static final int RequestPermissionCode = 7;
    private static final int REQUEST_ENABLE_BT = 0;
    private LocationManager locationManager;

    BluetoothSocket btSocket = null;
    Set<BluetoothDevice> pairedDevices;
    private InputStream inputStream;
    OutputStream outputStream;
    boolean stopThread;
    byte buffer[];
    String sensorValue;
    String provider;
    String dName ="Smart Helmet",dAddress="00:18:E4:40:00:06";
    String longitude, latitude;
    String namee, numberrr;

    Button connectBtn, btnSOS;
    TextView deviceName, sosName, sosNumber;
    Switch bluetoothSwitch;

    BluetoothAdapter mBlueAdapter = BluetoothAdapter.getDefaultAdapter();

    DatabaseHelper databaseHelper = new DatabaseHelper(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_landing);

        btnSOS = (Button) findViewById(R.id.btnSOS);
        connectBtn = (Button) findViewById(R.id.connectBtn);
        deviceName = (TextView) findViewById(R.id.deviceName);
        sosName = (TextView) findViewById(R.id.sosName);
        sosNumber = (TextView) findViewById(R.id.sosNumber);
        bluetoothSwitch =(Switch) findViewById(R.id.bluetoothSwitch);

        Intent intent = new Intent(this,smartService.class);
        startService(intent);

        Cursor items =databaseHelper.getData();
        if(items.moveToFirst()){
            namee=items.getString(1);
            numberrr=items.getString(2);
            sosName.setText(namee);
            sosNumber.setText(numberrr);
        }
        if (mBlueAdapter.isEnabled()){
            bluetoothSwitch.setChecked(true);
        }

        final int locationCheck = ContextCompat.checkSelfPermission(Landing.this, ACCESS_FINE_LOCATION);
        final int callCheck = ContextCompat.checkSelfPermission(Landing.this, CALL_PHONE);
        final int smsCheck = ContextCompat.checkSelfPermission(Landing.this, SEND_SMS);
        if(!((locationCheck & callCheck &smsCheck) == PackageManager.PERMISSION_GRANTED)){
            ActivityCompat.requestPermissions(Landing.this,new String[]{ACCESS_FINE_LOCATION,SEND_SMS,CALL_PHONE},RequestPermissionCode);
        }


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        provider = locationManager.getBestProvider(createFineCriteria(), true);
        locationManager.requestLocationUpdates(provider, 1000, 0,this);


        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true){
                    Toast.makeText(Landing.this, "Turning On Bluetooth...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, REQUEST_ENABLE_BT);
                }
                else {
                    Toast.makeText(Landing.this, "Turning Off Bluetooth...", Toast.LENGTH_SHORT).show();
                    mBlueAdapter = BluetoothAdapter.getDefaultAdapter();
                    mBlueAdapter.disable();
                    deviceName.setText("Waiting...");
                }
            }
        });

        btnSOS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                i.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(i, PICK_CONTACT);
            }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mBlueAdapter.isEnabled()){
                    Toast.makeText(Landing.this, "Turn On Your Bluetooth...", Toast.LENGTH_LONG).show();
                }
                else{
                    try {
                        bluetoothConnect();
                        if(btSocket.isConnected()){
                            beginListenForData();
                        }

                    } catch (Exception e) {
                        Toast.makeText(Landing.this, "Cannot connect at the moment.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

    }
    public static Criteria createFineCriteria(){

        Criteria c = new Criteria();
        c.setAccuracy(Criteria.ACCURACY_FINE);
        c.setAltitudeRequired(false);
        c.setBearingRequired(false);
        c.setSpeedRequired(false);
        c.setCostAllowed(true);
        c.setPowerRequirement(Criteria.POWER_HIGH);
        return c;

    }
    protected void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);
        String name = null, number = null;
        Log.i(TAG, "onActivityResult()");
        if (reqCode == PICK_CONTACT) {
            if (resultCode == Activity.RESULT_OK) {
                Uri contactsData = data.getData();
                CursorLoader loader = new CursorLoader(this, contactsData, null, null, null, null);
                Cursor c = loader.loadInBackground();
                if (c.moveToFirst()) {
                    name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    number = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    sosName.setText(name);
                    sosNumber.setText(number);
                }
                databaseHelper.addData(name,number);
            }
        }
    }

    protected void bluetoothConnect() throws IOException {

        mBlueAdapter = BluetoothAdapter.getDefaultAdapter();
        try {
            pairedDevices = mBlueAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice bt : pairedDevices) {
                    address = bt.getAddress();
                    name = bt.getName();
                    if(dName.equals(name) && dAddress.equals(address)){
                        BluetoothDevice bluetoothDevice = mBlueAdapter.getRemoteDevice(address);
                        btSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(myUUID);
                        btSocket.connect();
                        if (btSocket.isConnected()) {
                            try {
                                outputStream = btSocket.getOutputStream();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                inputStream = btSocket.getInputStream();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        try {
                            deviceName.setText(name);
                        }
                        catch (Exception e) {
                            btSocket.close();
                            e.printStackTrace();
                        }
                    }

                }
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(Landing.this, "Smart Helmet not discovered.", Toast.LENGTH_LONG).show();

        }
    }

    private void beginListenForData() {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[2048];

        System.out.println("buffer = " + buffer[1]);
        Thread thread = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopThread) {
                    try {
                        StringBuilder stringBuilder = new StringBuilder();
                        String line = null;
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
                        line = bufferedReader.readLine();
                        stringBuilder.append(line);
                        sensorValue = stringBuilder.toString();
                        final int sValue = Integer.valueOf(sensorValue);
                        System.out.println("*****************************************************************************************************************************************************************************************");
                        System.out.println("The output is:" + sensorValue);
                        System.out.println("*****************************************************************************************************************************************************************************************");

                        handler.post(new Runnable() {
                            public void run() {
                                if (sValue >= 30000) {
                                    int permissionCheck = ContextCompat.checkSelfPermission(Landing.this, Manifest.permission.SEND_SMS);
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        sendMessage();
                                    } else {
                                        ActivityCompat.requestPermissions(Landing.this, new String[]{Manifest.permission.SEND_SMS}, RequestPermissionCode);
                                    }
                                }
                            }
                        });
                    } catch (IOException ex) {
                        stopThread = true;
                    }
                }
            }
        });

        thread.start();
    }

    private void sendMessage() {

        String uri = "http://maps.google.com/maps?q="+latitude+","+longitude;
        String message = "I have met with an accident at this location.\n"+ uri;
        String phone = (String) sosNumber.getText();
        if(!phone.isEmpty()){
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage((String) sosNumber.getText(),null,message,null,null);
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse(phone));
            Toast.makeText(Landing.this, "Message Sent", Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(Landing.this, "Please add the SOS contact number!!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode){
            case RequestPermissionCode:
                if (grantResults.length>=0) {
                    boolean smsPermission =grantResults[0]==PackageManager.PERMISSION_GRANTED;
                    boolean locationPermission =grantResults[1]==PackageManager.PERMISSION_GRANTED;
                    boolean callPermission = grantResults[2]==PackageManager.PERMISSION_GRANTED;
                }
                else{
                    Toast.makeText(Landing.this, "Request not granted!!", Toast.LENGTH_LONG).show();
                }

        }


    }

    @Override
    public void onLocationChanged(Location location) {
        longitude = String.valueOf(location.getLongitude());
        latitude = String.valueOf(location.getLatitude());
        System.out.println("*****************************************************************************************************************************************************************************************");
        System.out.println("Latitude: "+latitude+"Longitude: "+longitude);
        System.out.println("*****************************************************************************************************************************************************************************************");

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}

class DatabaseHelper extends SQLiteOpenHelper{
    private static final String TABLE_NAME="sos_table";
    private static final String COL1 ="sosName";
    private static final String COL2 ="sosNumber";

    public DatabaseHelper(Context context) {
        super(context,TABLE_NAME,null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable ="CREATE TABLE "+ TABLE_NAME+"(ID INTEGER PRIMARY KEY AUTOINCREMENT, "+COL1+" STRING(255),"+COL2+" STRING(255))";
        String insert = "INSERT INTO sos_table VALUES(1,'Add Name','Add Number')";
        db.execSQL(createTable);
        db.execSQL(insert);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
    public boolean addData(String name,String number){
        String updateTable = "UPDATE "+TABLE_NAME+" SET "+COL1+" = '"+name+"', "+COL2+" = '"+number +"' WHERE ID=1";
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(updateTable);
        return true;
    }
    public Cursor getData(){
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "SELECT * FROM sos_Table";
        Cursor data =db.rawQuery(query,null);
        return data;
    }
}
