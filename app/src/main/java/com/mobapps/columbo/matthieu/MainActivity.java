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

import java.io.DataOutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String MATTHIEU_SERVER_IP_ADDR = "192.168.4.1";
    private final int MATTHIEU_SERVER_IP_PORT = 80;
    private final int UPDATE_PERIOD_MS = 100;

    private Button connectBtn;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private Handler mainHandler;

    // Member variables for the communication from network thread towards main (GUI) thread
    private boolean connectReq = false;
    private boolean connectAck = false;

    // Member variables for the communication from main (GUI) thread towards network thread
    private boolean boatCmdFlag = false;
    private int boatDirection = 0;
    private int boatSpeed = 0;

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
        // Many sensors return 3 values, one for each axis.
        // TODO: set boatDirection and enable boatCmdFlag
        float accY;
        accY = event.values[1];
    }

    private Runnable guiUpdate = new Runnable() {
        @Override
        public void run() {
            if(connectReq && connectAck)
                connectBtn.setBackgroundColor(Color.GREEN);
            if(connectReq && !connectAck)
                connectBtn.setBackgroundColor(Color.YELLOW);
            if(!connectReq && connectAck)
                connectBtn.setBackgroundColor(Color.YELLOW);
            if(!connectReq && !connectAck)
                connectBtn.setBackgroundColor(Color.RED);
            mainHandler.postDelayed(guiUpdate, UPDATE_PERIOD_MS);
        }
    };

    // Define a runnable periodically checking for connection and sending a string which contains
    // all the control information needed to drive the boat
    private Runnable networkUpdate = new Runnable() {
        @Override
        public void run() {
            String updateString;
            while(true) {
                if(connectReq) {
                    if(connectAck) {
                        // TODO: build and send update string only if needed
                        updateString = "";
                        updateString = updateString + "\\";
                        updateString = updateString + "A0D0";
                        updateString = updateString + ("/");
                        try {
                            outToServer.writeBytes(updateString);
                            Log.d("UD_MSG", updateString);
                        } catch (Exception e) {
                            Log.d("EXC_ERR",e.getMessage());
                        }
                    } else {
                        try {
                            clientSocket = new Socket(MATTHIEU_SERVER_IP_ADDR, MATTHIEU_SERVER_IP_PORT);
                            if(clientSocket != null)
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            // TODO: add a simple dialog with app name, version and authors
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
    }
}
