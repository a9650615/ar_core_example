package com.example.arcoreexample.model;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ModelLinksManager {

    public Map<String, String> modelList;

    public ModelLinksManager() {
        modelList = new HashMap<String,String>();
        modelList.put("房子", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/house.glb?alt=media&token=685708ed-c9f8-46cd-af19-b48c49df511a");
//        modelList.put("狐狸", "https://poly.googleusercontent.com/downloads/c/fp/1563760651765431/1dWAYYfUAhn/3MEx9PZHD_W/model.gltf");
        Runnable runnable = new Runnable(){
            @Override
            public void run() {
                onLoadJson();
            }
        };
        new Thread(runnable).start();
    }

    public void onLoadJson() {
        HttpHandler sh = new HttpHandler();
        // Making a request to url and getting response
        String url = "https://raw.githubusercontent.com/a9650615/ar_object_lists/master/fileLists.json?token=ADGCFQSLI2QFLJNUOHFWV6C5JPAIY";
        String jsonStr = sh.makeServiceCall(url);

        Log.e("ModelLinksManager", "Response from url: " + jsonStr);
        if (jsonStr != null) {
            try {
                JSONObject jsonObj = new JSONObject(jsonStr);

                // Getting JSON Array node
                JSONArray objects = jsonObj.getJSONArray("objects");
                modelList.clear();
                if (objects.length() > 0) {
                    // looping through All Contacts
                    for (int i = 0; i < objects.length(); i++) {
                        JSONObject c = objects.getJSONObject(i);
                        String name = c.getString("name");
                        String objUrl = c.getString("url");
                        modelList.put(name, objUrl);
                    }
                }
            } catch (final JSONException e) {
                Log.e("ModelLinksManager", "Json parsing error: " + e.getMessage());
            }

        } else {
            Log.e("ModelLinksManager", "Couldn't get json from server.");
        }

    }

}
