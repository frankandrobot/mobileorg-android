package com.matburt.mobileorg.Settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.content.Context;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import java.util.ArrayList;
import android.widget.EditText;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;
import com.matburt.mobileorg.R;

public class WizardActivity extends Activity
    implements RadioGroup.OnCheckedChangeListener {
    static String TAG="WizardActivity";
    
    //container
    PageFlipView wizard;
    //page 1 variables
    int syncWebDav, syncDropBox, syncSdCard;
    RadioGroup syncGroup; 
    //page 2 variables
    View loginPage;
    ArrayList<EditText> loginBoxes = new ArrayList<EditText>();
    boolean loginAdded=false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wizard);
	wizard = (PageFlipView) findViewById(R.id.wizard_parent);
	//setup page 1
	// PageView page1Container = (PageView) findViewById(R.id.wizard_page1);
	// LayoutInflater inflater=
	//     (LayoutInflater) getApplicationContext()
	//     .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	// View page1=inflater.inflate(R.layout.wizard_page1,page1Container);
	//get ids and pointers to sync radio buttons
	syncGroup = (RadioGroup) findViewById(R.id.sync_group);
	syncWebDav = ( (RadioButton) 
			findViewById(R.id.sync_webdav) ).getId();
	syncDropBox = ( (RadioButton) 
			findViewById(R.id.sync_dropbox) ).getId();
	syncSdCard = ( (RadioButton) 
			findViewById(R.id.sync_sdcard) ).getId();
	syncGroup.clearCheck();
	//setup click listener for sync radio group
	syncGroup.setOnCheckedChangeListener(this);
    }

    /**
     * Upon being resumed we can retrieve the current state.  This allows us
     * to update the state if it was changed at any time while paused.
     */
    @Override
    protected void onResume() {
        super.onResume();
	Log.d(TAG,"onResume: loading... "+String.valueOf(wizard.getCurrentPage()));
	wizard.restoreLastPage();
	Log.d(TAG,"onResume: done... "+String.valueOf(wizard.getCurrentPage()));
    }

    /**
     * Any time we are paused we need to save away the current state, so it
     * will be restored correctly when we are resumed.
     */
    @Override
    protected void onPause() {
        super.onPause();
	Log.d(TAG,"saving state>>>>>>>>>>>>>>>>>>>>>");
	Log.d(TAG,"onPause: "+String.valueOf(wizard.getCurrentPage()));
	wizard.saveCurrentPage();
    }

    @Override
    	public void onCheckedChanged(RadioGroup arg, int checkedId) {
    	SharedPreferences appSettings = 
    	    PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	SharedPreferences.Editor editor = appSettings.edit();
    	if ( checkedId == syncWebDav )
    	    editor.putString("syncSource", "webdav");
    	else if ( checkedId == syncDropBox ) {
    	    //editor.putString("syncSource", "dropbox");
	    createDropboxLogin();
	}
    	else if ( checkedId == syncSdCard)
    	    editor.putString("syncSource", "sdcard");
    	editor.commit();
    }
    
    void createDropboxLogin() {
	ViewGroup page2 = (ViewGroup) 
	    findViewById(R.id.wizard_page2_container); //parent scrollview
	page2 = (ViewGroup) page2.getChildAt(0); //linearlayout
	LayoutInflater inflater=
	    (LayoutInflater) LayoutInflater.from(getApplicationContext());
	loginPage = inflater.inflate(R.layout.wizard_dropbox,
				     null);
	if ( loginAdded ) page2.removeViewAt(0);
	page2.addView(loginPage, 0);
	loginAdded = true;
    }

    /* TODO: Unfocus login textboxes on orientation change */
}
