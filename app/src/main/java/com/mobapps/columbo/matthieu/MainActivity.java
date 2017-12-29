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
import android.widget.ToggleButton;
import android.widget.SeekBar;

import java.io.DataOutputStream;
import java.net.Socket;

// ToDo: verify functionality of periodically sending information about how to drive the boat, e.g. "\D7L3/"
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String MATTHIEU_SERVER_IP_ADDR = "192.168.4.1";
    private final int MATTHIEU_SERVER_IP_PORT = 80;
    private final int UPDATE_PERIOD_MS = 100;

    private Button connectBtn;
    private SeekBar speedCtrl;
    private ToggleButton brakeCtrl;
    private ToggleButton backwardCtrl;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private Handler mainHandler;
    private Handler networkHandler;

    private boolean connectReq = false;
    private boolean connectAck = false;
    private int speed = 0;
    private boolean brake = false;
    private boolean backward = false;
    private float accY = 0;

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

        // Define a seekbar to allow user specify a speed to drive the boat
        speedCtrl = (SeekBar) findViewById(R.id.speed_ctrl);
        speedCtrl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speed = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Define a toggle button to allow the user stop the boat
        brakeCtrl = (ToggleButton) findViewById(R.id.brake_ctrl);
        brakeCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                brake = brakeCtrl.isChecked();
            }
        });

        // Define a toggle button to allow the user make the boat go backward
        backwardCtrl = (ToggleButton) findViewById(R.id.backward_ctrl);
        backwardCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backward = backwardCtrl.isChecked();
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

    // Define a runnable periodically sending a string which contains all the control information needed to drive the boat
    private Runnable networkUpdate = new Runnable() {
        @Override
        public void run() {
            while(true) {
                if(connectReq) {
                    if(connectAck) {
                        String updateString;
                        int absAccY = 0;
                        updateString = "";
                        updateString = updateString + "\\";
                        if(brakeCtrl.isChecked())
                            updateString = updateString + "B";
                        else
                        if(backwardCtrl.isChecked())
                            updateString = updateString + "R";
                        else
                            updateString = updateString + "D";
                        updateString = updateString + Integer.toString(speedCtrl.getProgress()/10);
                        updateString = updateString + (accY > 0 ? "R" : "L");
                        absAccY = Math.abs(Math.round(accY));
                        updateString = updateString + ((absAccY > 9) ? 9 : absAccY);
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

    /*
    private Socket clientSocket;
    private DataOutputStream outToServer;
    AlertDialog alertDialog;

    private Handler mainHandler;
    private Handler connectHandler;

    private float accY = 0;

    private Runnable sendUpdate = new Runnable(){
        @Override
        public void run() {
            String updateString;
            int absAccY = 0;
            Log.d("PROGRESS", String.format("%d",speedCtrl.getProgress()/10));
            Log.d("ACC_Y", String.format("%.2f",accY));
            updateHandler.postDelayed(sendUpdate, UPDATE_PERIOD_MS);
            if (clientSocket != null && clientSocket.isConnected()) {
                updateString = "";
                updateString = updateString + "\\";
                if(brakeCtrl.isChecked())
                    updateString = updateString + "B";
                else
                if(backwardCtrl.isChecked())
                    updateString = updateString + "R";
                else
                    updateString = updateString + "D";
                updateString = updateString + Integer.toString(speedCtrl.getProgress()/10);
                updateString = updateString + (accY > 0 ? "R" : "L");
                absAccY = Math.abs(Math.round(accY));
                updateString = updateString + ((absAccY > 9) ? 9 : absAccY);
                updateString = updateString + ("/");
                try {
//                    outToServer.writeBytes(updateString);
                    Log.d("UD_MSG", updateString);
                } catch (Exception e) {
                    Log.d("EXC_ERR",e.getMessage());
                }
            }
       }
    };

    private Thread connectThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (clientSocket == null || !clientSocket.isConnected()) {
                try {
                    clientSocket = new Socket(MATTHIEU_SERVER_IP_ADDR, MATTHIEU_SERVER_IP_PORT);
                    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                    connectBtn.setBackgroundColor(Color.GREEN);
                } catch(Exception e) {
                    alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Connection error");
                    alertDialog.setMessage("Couldn't connect to Matthieu server (" + MATTHIEU_SERVER_IP_ADDR + ":" + MATTHIEU_SERVER_IP_PORT + "). " +
                            "Please check that the correct WiFi AP is being used and re-try.");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            } else {
                try {
                    clientSocket.close();
                    connectBtn.setBackgroundColor(Color.RED);
                } catch(Exception e) {
                    alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Connection error");
                    alertDialog.setMessage("Couldn't disconnect from Matthieu server (" + MATTHIEU_SERVER_IP_ADDR + ":" + MATTHIEU_SERVER_IP_PORT + "). " +
                            "Please restart both client and server applications.");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        }
    });

*/
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
        if (id == R.id.action_settings) {
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
