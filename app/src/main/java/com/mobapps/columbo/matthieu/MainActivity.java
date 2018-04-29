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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
    private String boatFBAction = "F";
    private int    boatFBGrade  = 0;
    private String boatLRAction = "L";
    private int    boatLRGrade  = 0;

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
                if (boatFBAction == "B") {
                    if (boatFBGrade == 0) {
                        boatFBGrade = 6;
                    } else if (boatFBGrade == 6) {
                        boatFBGrade = 9;
                    }
                } else {
                    if (boatFBGrade == 9) {
                        boatFBGrade = 6;
                    } else if (boatFBGrade == 6) {
                        boatFBGrade = 0;
                    } else {
                        boatFBAction = "B";
                        boatFBGrade = 6;
                    }
                }
                boatCmdFlag = true;
            }
        });

        fwdBtn = (Button) findViewById(R.id.fwd_btn);
        fwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (boatFBAction == "F") {
                    if (boatFBGrade == 0) {
                        boatFBGrade = 6;
                    } else if (boatFBGrade == 6) {
                        boatFBGrade = 9;
                    }
                } else {
                    if (boatFBGrade == 9) {
                        boatFBGrade = 6;
                    } else if (boatFBGrade == 6) {
                        boatFBGrade = 0;
                    } else {
                        boatFBAction = "F";
                        boatFBGrade = 6;
                    }
                }
            }
        });

        neutralBtn = (Button) findViewById(R.id.neutral_btn);
        neutralBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boatFBGrade = 0;
                boatCmdFlag = true;
            }
        });

        debugTxt =(TextView) findViewById(R.id.debug_txt);

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
            boatLRGrade = (Math.abs(Math.round(accY)));
            if (boatLRGrade > 3) {
                boatLRGrade = 1;
            } else {
                boatLRGrade = 0;
            }
            boatLRAction = (accY < 0) ? "L" : "R";
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

            debugTxt.setText("Motor: " + ((boatFBGrade != 0) ? ((boatFBAction == "F") ? "Forward" : "Backward") : "Stop"));
            mainHandler.postDelayed(guiUpdate, UPDATE_PERIOD_MS);
        }
    };

    // Define a runnable periodically checking for connection and sending a string which contains
    // all the control information needed to drive the boat
    private Runnable networkUpdate = new Runnable() {
        @Override
        public void run() {
            while (true) {
                if (connectReq) {
                    if (connectAck) {
                        updateString = "";
                        updateString = updateString + "\\";
                        updateString = updateString + boatFBAction + boatFBGrade;
                        updateString = updateString + boatLRAction + boatLRGrade;
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
            actionTxt = actionTxt + "Version  : 1.1" + "\n";
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
        updateString = updateString + "F0L0";
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
