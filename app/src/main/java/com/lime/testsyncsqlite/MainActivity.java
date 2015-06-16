package com.lime.testsyncsqlite;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.lime.testsyncsqlite.R;

import java.net.*;
import java.util.*;

import org.apache.http.conn.util.InetAddressUtils;

public class MainActivity extends ActionBarActivity {
    //DB Class to perform DB related operations
    DBController controller = new DBController(this);
    //Progress Dialog Object
    ProgressDialog prgDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Get User records from SQLite DB
        ArrayList<HashMap<String, String>> userList = controller.getAllUsers();
        //
        if (userList.size() != 0) {
            //Set the User Array list in ListView
            ListAdapter adapter = new SimpleAdapter(MainActivity.this, userList, R.layout.view_user_entry, new String[]{"userId", "userName"}, new int[]{R.id.userId, R.id.userName});
            ListView myList = (ListView) findViewById(android.R.id.list);
            myList.setAdapter(adapter);
            //Display Sync status of SQLite DB
            Toast.makeText(getApplicationContext(), controller.getSyncStatus(), Toast.LENGTH_LONG).show();
        }
        //Initialize Progress Dialog properties
        prgDialog = new ProgressDialog(this);
        prgDialog.setMessage("Synching SQLite Data with Remote MySQL DB. Please wait...");
        prgDialog.setCancelable(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //When Sync action button is clicked
        if (id == R.id.refresh) {
            //Sync SQLite DB data to remote MySQL DB
            syncSQLiteMySQLDB();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //Add User method getting called on clicking (+) button
    public void addUser(View view) {
        Intent objIntent = new Intent(getApplicationContext(), NewUser.class);
        startActivity(objIntent);
    }

    public String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim < 0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    public void syncSQLiteMySQLDB() {
        //Create AsycHttpClient object
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        ArrayList<HashMap<String, String>> userList = controller.getAllUsers();
        if (userList.size() != 0) {
            if (controller.dbSyncCount() != 0) {
                prgDialog.show();
                params.put("usersJSON", controller.composeJSONfromSQLite());
                String url = "http://" + "172.30.10.207" + ":9080/WatchAssemblyWebServer/saveUsers.do";
                Log.d("url", url);
                client.post(url, params, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(String response) {
                        System.out.println(response);
                        prgDialog.hide();
                        try {
                            JSONArray arr = new JSONArray(response);
                            System.out.println(arr.length());
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = (JSONObject) arr.get(i);
                                System.out.println(obj.get("userId"));
                                System.out.println(obj.get("userStatus"));
                                controller.updateSyncStatus(obj.get("userId").toString(), obj.get("userStatus").toString());
                            }
                            Toast.makeText(getApplicationContext(), "DB Sync completed!", Toast.LENGTH_LONG).show();
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            Toast.makeText(getApplicationContext(), "Error Occured [Server's JSON response might be invalid]!", Toast.LENGTH_LONG).show();
                            Log.d("success-json error", e.toString());
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Throwable error,
                                          String content) {
                        // TODO Auto-generated method stub
                        prgDialog.hide();
                        if (statusCode == 404) {
                            Toast.makeText(getApplicationContext(), "Requested resource not found", Toast.LENGTH_LONG).show();
                            Log.d("fail", "Requested resource not found");
                        } else if (statusCode == 500) {
                            Toast.makeText(getApplicationContext(), "Something went wrong at server end", Toast.LENGTH_LONG).show();
                            Log.d("fail", "Something went wrong at server end");
                        } else {
                            Toast.makeText(getApplicationContext(), "Unexpected Error occcured! [Most common Error: Device might not be connected to Internet]", Toast.LENGTH_LONG).show();
                            Log.d("fail", statusCode + " Unexpected Error occcured! [Most common Error: Device might not be connected to Internet]");
//							Log.d("fail.content", content);
                        }
                    }
                });
            } else {
                Toast.makeText(getApplicationContext(), "SQLite and Remote MySQL DBs are in Sync!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "No data in SQLite DB, please do enter User name to perform Sync action", Toast.LENGTH_LONG).show();
        }
    }

}
