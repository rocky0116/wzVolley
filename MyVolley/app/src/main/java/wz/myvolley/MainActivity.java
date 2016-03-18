package wz.myvolley;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.wz.wzvolley.http.WzHttp;

import java.util.HashMap;
import java.util.Map;

import wz.myvolley.View.CustomProgressDialog;

public class MainActivity extends AppCompatActivity {

    String url="http://124.163.231.2:8095/YrdUtilService.asmx/getCity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        aa();
    }

    private void aa(){
        CustomProgressDialog progressDialog = new CustomProgressDialog(this, "正在加载中......", R.anim.donghua_frame);
        Map<String,String> map=new HashMap<String,String>();
        map.put("pcode", "21");
        WzHttp wzHttp=new WzHttp();
        wzHttp.get(this,url,map,progressDialog);
        wzHttp.post(this, url, map, progressDialog);
    }
}
