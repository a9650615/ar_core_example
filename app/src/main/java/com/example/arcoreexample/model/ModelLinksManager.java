package com.example.arcoreexample.model;

import java.util.HashMap;
import java.util.Map;

public class ModelLinksManager {

    public Map<String, String> modelList;

    public ModelLinksManager() {
        modelList = new HashMap<String,String>();
        modelList.put("房子", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/house.glb?alt=media&token=685708ed-c9f8-46cd-af19-b48c49df511a");
//        modelList.put("狐狸", "https://poly.googleusercontent.com/downloads/c/fp/1563760651765431/1dWAYYfUAhn/3MEx9PZHD_W/model.gltf");
        modelList.put("玻璃球", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/ttt.glb?alt=media&token=eece1681-2255-43c2-9f9f-798706450c82");
        modelList.put("Vive", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/vive.glb?alt=media&token=ad64c2b6-fb47-4f82-8da3-9d2a42cde609");
        modelList.put("狐狸", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/fox.glb?alt=media&token=a868fb05-18fd-4e7e-812e-597627c1bfa6");
        modelList.put("Park(多模)", "https://firebasestorage.googleapis.com/v0/b/arcorecloud-246504.appspot.com/o/parkingSupervisor.glb?alt=media&token=dd2c165f-8594-4a8a-a740-ac6e5286faf4");
    }


}
