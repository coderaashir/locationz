package com.score.senz.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.score.senz.R;
import com.score.senz.application.SenzApplication;
import com.score.senz.db.SenzorsDbSource;
import com.score.senz.enums.SenzTypeEnum;
import com.score.senz.exceptions.NoUserException;
import com.score.senz.pojos.Senz;
import com.score.senz.services.SenzService;
import com.score.senz.utils.ActivityUtils;
import com.score.senz.utils.NetworkUtil;
import com.score.senz.utils.PreferenceUtils;
import com.score.senz.utils.RSAUtils;
import com.score.senz.utils.SenzParser;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;

/**
 * Activity class which handler switching
 *
 * @author eranga herath(erangaeb@gmail.com)
 */
public class SenzSwitchBoardActivity extends Activity implements View.OnClickListener {

    private static final String TAG = SenzSwitchBoardActivity.class.getName();

    // UI components
    private RelativeLayout nightModeButton;
    private RelativeLayout visitorModeButton;
    private TextView nightModeText;
    private TextView visitorModeText;
    private Switch nightModeSwitch;
    private Switch visitorModeSwitch;

    // use custom font here
    private Typeface typeface;

    // keeps weather service already bound or not
    boolean isServiceBound = false;

    // use to send senz messages to SenzService
    Messenger senzServiceMessenger;

    // connection for SenzService
    private ServiceConnection senzServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            senzServiceMessenger = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            senzServiceMessenger = null;
        }
    };

    // use to track response timeout
    private SenzCountDownTimer senzCountDownTimer;
    private boolean isResponseReceived;

    // this activity deals with senz
    private Senz thisSenz;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.senz_switch_board_layout);

        typeface = Typeface.createFromAsset(getAssets(), "fonts/vegur_2.otf");

        senzCountDownTimer = new SenzCountDownTimer(16000, 5000);
        isResponseReceived = false;
        setUpActionBar();
        initThisSenz();
        initUi();
    }

    /**
     * Set action bar title and font
     */
    private void setUpActionBar() {
        ActionBar actionBar = getActionBar();
        int titleId = getResources().getIdentifier("action_bar_title", "id", "android");
        TextView actionBarTitle = (TextView) (this.findViewById(titleId));

        Typeface typefaceThin = Typeface.createFromAsset(this.getAssets(), "fonts/vegur_2.otf");
        actionBarTitle.setTextColor(getResources().getColor(R.color.white));
        actionBarTitle.setTypeface(typefaceThin);

        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("#Gpio");
    }

    /**
     * Initialize UI components
     */
    private void initUi() {
        nightModeButton = (RelativeLayout) findViewById(R.id.night_mode);
        visitorModeButton = (RelativeLayout) findViewById(R.id.visitor_mode);

        nightModeText = (TextView) findViewById(R.id.night_mode_text);
        visitorModeText = (TextView) findViewById(R.id.visitor_mode_text);

        nightModeSwitch = (Switch) findViewById(R.id.night_mode_switch);
        visitorModeSwitch = (Switch) findViewById(R.id.visitor_mode_switch);

        nightModeText.setTypeface(typeface, Typeface.BOLD);
        visitorModeText.setTypeface(typeface, Typeface.BOLD);

        nightModeSwitch.setOnClickListener(this);
        visitorModeSwitch.setOnClickListener(this);

        // set up switches according to ON, OFF state
//        if (thisSenz.getAttributes().get("gpio3").equalsIgnoreCase("ON")) {
//            nightModeButton.setBackgroundResource(R.drawable.green_button_selector);
//            nightModeSwitch.setChecked(true);
//        } else {
//            nightModeButton.setBackgroundResource(R.drawable.disable_bg);
//            nightModeSwitch.setChecked(false);
//        }
    }

    /**
     * Initialize Senz object from here
     */
    private void initThisSenz() {
//        Bundle bundle = getIntent().getExtras();
//        if (bundle != null) {
//            this.thisSenz = bundle.getParcelable("extra");
//        }
        this.thisSenz = ((SenzApplication) getApplication()).getSenz();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();

        // bind to senz service
        if (!isServiceBound) {
            bindService(new Intent(SenzSwitchBoardActivity.this, SenzService.class), senzServiceConnection, Context.BIND_AUTO_CREATE);
            isServiceBound = true;
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(senzMessageReceiver, new IntentFilter("DATA"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the service
        if (isServiceBound) {
            unbindService(senzServiceConnection);
            isServiceBound = false;
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(senzMessageReceiver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        this.overridePendingTransition(R.anim.stay_in, R.anim.right_out);
    }

    /**
     * Receive broadcasting messages from senz service
     */
    private BroadcastReceiver senzMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Got message from Senz service");
            handleMessage(intent);
        }
    };

    /**
     * Handle broadcast message receives
     * Need to handle registration success failure here
     *
     * @param intent intent
     */
    private void handleMessage(Intent intent) {
        String action = intent.getAction();

        if (action.equals("DATA")) {
            boolean isDone = intent.getExtras().getBoolean("extra");

            // response received
            ActivityUtils.cancelProgressDialog();
            isResponseReceived = true;
            senzCountDownTimer.cancel();

            // on successful share display notification message(Toast)
            if (isDone) onPostPut();
        }
    }

    /**
     * Keep track with share response timeout
     */
    private class SenzCountDownTimer extends CountDownTimer {

        public SenzCountDownTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            // if response not received yet, resend share
            if (!isResponseReceived) {
                // if switch is on we have to off
                // if switch if off we have to on
                boolean on = (thisSenz.getAttributes().get("gpio3").equalsIgnoreCase("ON")) ? false : true;
                put(on);
                Log.d(TAG, "Response not received yet");
            }
        }

        @Override
        public void onFinish() {
            ActivityUtils.cancelProgressDialog();

            // display message dialog that we couldn't reach the user
            if (!isResponseReceived) {
                // TODO
                String message = "<font color=#000000>Seems we couldn't switch the </font> <font color=#ffc027>" + "<b>" + "NIGHT MODE" + "</b>" + "</font> <font color=#000000> at this moment</font>";
                displayInformationMessageDialog("#Put Fail", message);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v == nightModeSwitch) {
            handleSwitchButtonClick(true);
        } else if (v == visitorModeSwitch) {
            handleSwitchButtonClick(false);
        }
    }

    private void handleSwitchButtonClick(boolean isNightMode) {
        if (NetworkUtil.isAvailableNetwork(this)) {
            ActivityUtils.showProgressDialog(this, "Please wait...");
            senzCountDownTimer.start();
        } else {
            Toast.makeText(this, "No network connection available", Toast.LENGTH_LONG).show();
        }
    }

    private void onPostPut() {
        boolean on = (thisSenz.getAttributes().get("gpio3").equalsIgnoreCase("ON")) ? false : true;
        if (on) {
            // update db
            new SenzorsDbSource(this).updateSenz(thisSenz.getSender(), "ON");

            // update switches
            nightModeButton.setBackgroundResource(R.drawable.green_button_selector);
            nightModeSwitch.setChecked(true);

            Toast.makeText(this, "Successfully switched on", Toast.LENGTH_LONG).show();
        } else {
            // update db
            new SenzorsDbSource(this).updateSenz(thisSenz.getSender(), "OFF");

            // update switches
            nightModeButton.setBackgroundResource(R.drawable.disable_bg);
            nightModeSwitch.setChecked(false);

            Toast.makeText(this, "Successfully switched off", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * PUT senz
     * Need to send PUT query to server via senz service
     */
    private void put(boolean on) {
        try {
            // create key pair
            PrivateKey privateKey = RSAUtils.getPrivateKey(this);

            // create senz attributes
            HashMap<String, String> senzAttributes = new HashMap<>();
            String gpioValue = on ? "ON" : "OFF";
            senzAttributes.put("gpio3", gpioValue);
            senzAttributes.put("time", ((Long) (System.currentTimeMillis() / 1000)).toString());

            // TODO
            // new senz
            Senz senz = new Senz();
            senz.setSenzType(SenzTypeEnum.PUT);
            //senz.setReceiver(new User("", usernameEditText.getText().toString().trim()));
            senz.setSender(PreferenceUtils.getUser(this));
            senz.setAttributes(senzAttributes);

            // get digital signature of the senz
            String senzPayload = SenzParser.getSenzPayload(senz);
            String senzSignature = RSAUtils.getDigitalSignature(senzPayload.replaceAll(" ", ""), privateKey);
            String senzMessage = SenzParser.getSenzMessage(senzPayload, senzSignature);

            // send senz to server
            Message msg = new Message();
            msg.obj = senzMessage;
            senzServiceMessenger.send(msg);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | NoUserException | SignatureException | RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Display message dialog when user request(click) to delete invoice
     *
     * @param message message to be display
     */
    public void displayInformationMessageDialog(String title, String message) {
        final Dialog dialog = new Dialog(this);

        //set layout for dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.information_message_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        // set dialog texts
        TextView messageHeaderTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_header_text);
        TextView messageTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_text);
        messageHeaderTextView.setText(title);
        messageTextView.setText(Html.fromHtml(message));

        // set custom font
        messageHeaderTextView.setTypeface(typeface);
        messageTextView.setTypeface(typeface);

        //set ok button
        Button okButton = (Button) dialog.findViewById(R.id.information_message_dialog_layout_ok_button);
        okButton.setTypeface(typeface);
        okButton.setTypeface(null, Typeface.BOLD);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.show();
    }

}
