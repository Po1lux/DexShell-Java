package com.pollux.sourceapk;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SubActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tx = new TextView(this);
        tx.setText("here is sub activity");
        setContentView(tx);
        Log.i("cs","SubActivity app ctx: "+getApplicationContext());
    }
}
