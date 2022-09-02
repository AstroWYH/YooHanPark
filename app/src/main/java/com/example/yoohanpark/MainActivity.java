package com.example.yoohanpark;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity{
    CameraHelper cameraHelper = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.switchBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToastUtil.showMsg(MainActivity.this, "你点击了Switch");
            }
        });
    }

}