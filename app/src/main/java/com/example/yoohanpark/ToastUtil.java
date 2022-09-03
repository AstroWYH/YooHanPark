package com.example.yoohanpark;

import android.content.Context;
import android.widget.Toast;

public class ToastUtil {
    public static Toast toast_;
    public static void showMsg(Context context, String msg){
        if ((toast_ == null)){
            toast_ = Toast.makeText(context, TagUtil.YOOHAN + msg, Toast.LENGTH_LONG);
        }else {
            toast_.setText(msg);
        }
        toast_.show();
    }
}
