package com.wz.wzvolley.http;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;

import com.wz.wzvolley.Request;
import com.wz.wzvolley.RequestQueue;
import com.wz.wzvolley.Response;
import com.wz.wzvolley.VolleyError;
import com.wz.wzvolley.toolbox.StringRequest;
import com.wz.wzvolley.toolbox.Volley;

import java.util.Map;

/**
 * Created by Fly0116 on 2016/3/18 0018.
 */
public class WzHttp {


    public void get(Context cxt,String url,Map<String,String> map, final ProgressDialog dialog){
        dialog.show();
        RequestQueue mQueue = Volley.newRequestQueue(cxt);
        StringRequest stringRequest = new StringRequest(url,map,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("TAG", response);
                        dialog.dismiss();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("TAG", error.getMessage(), error);
                dialog.dismiss();
            }
        });
        mQueue.add(stringRequest);
    }

    public void post(Context cxt,String url,Map<String,String> map, final ProgressDialog dialog){
        if (dialog!=null){
            dialog.show();
        }
        RequestQueue mQueue=Volley.newRequestQueue(cxt);
        StringRequest stringRequest=new StringRequest(Request.Method.POST, "http://m.weather.com.cn/data/101010100.html", map, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d("postResponse", response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("postError", error.getMessage(), error);
                dialog.dismiss();
            }
        });

        mQueue.add(stringRequest);
    }

    public void getP(){
        try{
            System.out.print(1!=1);
            System.out.print(1+1==2&&1+1!=3);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
