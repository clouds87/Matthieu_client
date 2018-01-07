package com.mobapps.columbo.matthieu;

import android.content.pm.ActivityInfo;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompatSideChannelService;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import java.io.DataOutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String MATTHIEU_SERVER_IP_ADDR = "192.168.4.1";
    private final int MATTHIEU_SERVER_IP_PORT = 80;
    private final int UPDATE_PERIOD_MS = 100;
    private String updateString;

    private Button connectBtn;
    private Button bwdBtn;
    private Button neutralBtn;
    private Button fwdBtn;
    private TextView debugTxt;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private Handler mainHandler;

    // Member variables for the communication from network thread towards main (GUI) thread
    private boolean connectReq = false;
    private boolean connectAck = false;

    // Member variables for the communication from main (GUI) thread towards network thread
    private boolean boatCmdFlag = false;
    private String boatDirection = "";
    private int boatSpeed = 4;
    private String dir = "";
    private int valore = 0;

    private Socket clientSocket;
    private DataOutputStream outToServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Define a button to let the user connect to/disconnect from the Matthieu boat server
        connectBtn = (Button) findViewById(R.id.connect_btn);
        connectBtn.setBackgroundColor(Color.RED);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectReq = !connectReq;
            }
        });

        bwdBtn = (Button) findViewById(R.id.bwd_btn);
        bwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (boatSpeed > 1) {
                    boatSpeed = boatSpeed - 1;
                    boatCmdFlag = true;
                }
            }
        });

        fwdBtn = (Button) findViewById(R.id.fwd_btn);
        fwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (boatSpeed < 7) {
                    boatSpeed = boatSpeed + 1;
                    boatCmdFlag = true;
                }
            }
        });

        neutralBtn = (Button) findViewById(R.id.neutral_btn);
        neutralBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boatSpeed = 4;
                boatCmdFlag = true;
            }
        });

        debugTxt =(TextView)  findViewById(R.id.debug_txt);

        // Define a sensor manager and an accelerometer sensor to let the user control the steering
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Define an handler to a runnable periodically updating the GUI with all the status information coming back from the boat
        mainHandler = new Handler();
        mainHandler.postDelayed(guiUpdate, UPDATE_PERIOD_MS);

        // Define a thread with a runnable periodically updating the GUI with all the status information coming back from the boat
        networkThread.start();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {

        float accY;
        if (connectReq && connectAck) {
            accY = event.values[1];
            valore = (Math.abs(Math.round(accY)));
            if (valore > 9) valore = 9;
            valore = Math.round(valore / 3);
            dir = (accY < 0) ? "S" : "D";
            if (valore == 3) {
                valore = 2;
            } else if (valore == 2) {
                valore = 1;
            } else if (valore == 1) {
                valore = 0;
            }
             boatCmdFlag = true;
        }else{
            boatCmdFlag = false;
        }
     }

    private Runnable guiUpdate = new Runnable() {
        @Override
        public void run() {

            bwdBtn.setVisibility(View.INVISIBLE);
            neutralBtn.setVisibility(View.INVISIBLE);
            fwdBtn.setVisibility(View.INVISIBLE);
            boatCmdFlag = false;

            if (connectReq && connectAck) {
                connectBtn.setBackgroundColor(Color.GREEN);
                bwdBtn.setVisibility(View.VISIBLE);
                neutralBtn.setVisibility(View.VISIBLE);
                fwdBtn.setVisibility(View.VISIBLE);
            }
            if (connectReq && !connectAck)
                connectBtn.setBackgroundColor(Color.YELLOW);
            if (!connectReq && connectAck)
                connectBtn.setBackgroundColor(Color.YELLOW);
            if (!connectReq && !connectAck)
                connectBtn.setBackgroundColor(Color.RED);

            mainHandler.postDelayed(guiUpdate, UPDATE_PERIOD_MS);
        }
    };

    // Define a runnable periodically checking for connection and sending a string which contains
    // all the control information needed to drive the boat
    private Runnable networkUpdate = new Runnable() {
        @Override
        public void run() {
        int motore = 0;
            while (true) {
                if (connectReq) {
                    if (connectAck) {
                         if (boatSpeed >= 4) {
                            boatDirection = "A";        // avanti
                        } else{
                            boatDirection = "I";        // indietro
                        }
                        if (boatSpeed == 4){
                            motore = 0;     //spento
                        }else if(boatSpeed == 5){
                            motore = 1;     //piano
                        }else if(boatSpeed == 6){
                            motore = 2;     //mezza
                        }else if(boatSpeed == 7){
                            motore = 3;     //tutta
                        }else if(boatSpeed == 3){
                            motore = 1;     //piano
                        }else if(boatSpeed == 2){
                            motore = 2;     //mezza
                        }else if(boatSpeed == 1) {
                            motore = 3;     //tutta
                        }
                        updateString = "";
                        updateString = updateString + "\\";
                        updateString = updateString + boatDirection + motore;
                        updateString = updateString + dir + valore;
                        updateString = updateString + ("/");
                         try {
                            outToServer.writeBytes(updateString);
                            Log.d("UPD_MSG", updateString);
                        } catch (Exception e) {
                            Log.d("EXC_ERR", e.getMessage());
                        }
                    } else {
                        try {
                            clientSocket = new Socket(MATTHIEU_SERVER_IP_ADDR, MATTHIEU_SERVER_IP_PORT);
                            if (clientSocket != null)
                                outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            connectAck = true;
                        } catch (Exception e) {
                            Log.d("EXC_ERR", e.getMessage());
                        }
                    }
                } else {
                    if (connectAck) {
                        try {
                            clientSocket.close();
                            connectAck = false;
                        } catch (Exception e) {
                        }
                    }
                }
                try {
                    Thread.sleep(UPDATE_PERIOD_MS);
                } catch (Exception e) {
                }
            }
        }
    };
    private Thread networkThread = new Thread(networkUpdate);

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        String actionTxt;

        if (id == R.id.action_content) {
            actionTxt = "";
            actionTxt = actionTxt + "This app allows the total control";
            actionTxt = actionTxt + " of a dynamic naval model dedicated";
            actionTxt = actionTxt + " to a person very dear to us!";
            Toast content = Toast.makeText(MainActivity.this, actionTxt, Toast.LENGTH_LONG);
            content.setGravity(Gravity.VERTICAL_GRAVITY_MASK, 0, 0);
            content.show();
        }

        if (id == R.id.action_about) {
            actionTxt = "";
            actionTxt = actionTxt + "App name : Matthieu" + "\n";
            actionTxt = actionTxt + "Version  : 1.0" + "\n";
            actionTxt = actionTxt + "Authors  : Columbo's son and father";
            Toast about = Toast.makeText(MainActivity.this, actionTxt, Toast.LENGTH_LONG);
            about.setGravity(Gravity.DISPLAY_CLIP_VERTICAL, 0, 0);
            about.show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);

        updateString = "";
        updateString = updateString + "\\";
        updateString = updateString + "A0D0";
        updateString = updateString + ("/");
        try {
            outToServer.writeBytes(updateString);
            Log.d("UPD_MSG", updateString);
        } catch (Exception e) {
            Log.d("EXC_ERR", e.getMessage());
        }

        try {
            clientSocket.close();
            connectAck = false;
        } catch (Exception e) {
            Log.d("EXC_ERR", e.getMessage());
        }
    }
}
