package com.example.arcoreexample;

//import android.support.annotation.Nullable;

import androidx.annotation.Nullable;

import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class AnchorData {
    public String otherData;
    public CloudAnchorManager.CloudAnchorListener listener;

    public AnchorData(String data, CloudAnchorManager.CloudAnchorListener setListener) {
        otherData = data;
        listener = setListener;
    }
}

/**
 * A helper class to handle all the Cloud Anchors logic, and add a callback-like mechanism on top of
 * the existing ARCore API.
 */
class CloudAnchorManager {
    private static final String TAG =
            MainActivity.class.getSimpleName() + "." + CloudAnchorManager.class.getSimpleName();

    /** Listener for the results of a host or resolve operation. */
    interface CloudAnchorListener {

        /** This method is invoked when the results of a Cloud Anchor operation are available. */
        void onCloudTaskComplete(String anchor, Anchor cameraAnchorId, String otherData);

    }

    @Nullable
    private Session session = null;
    private final HashMap<Anchor, AnchorData> pendingAnchors = new HashMap<>();
    private Anchor cameraAnchor;
    private Anchor cameraCloudAnchor;
    private String lastAnchorId;

    synchronized void setCameraAnchor(Anchor newCameraAnchor) {
        cameraAnchor = newCameraAnchor;
    }

    /**
     * This method is used to set the session, since it might not be available when this object is
     * created.
     */
    synchronized void setSession(Session session) {
        this.session = session;
    }

    /**
     * This method hosts an anchor. The {@code listener} will be invoked when the results are
     * available.
     */
    synchronized void hostCloudAnchor(Anchor anchor, CloudAnchorListener listener, String otherData) {
        Preconditions.checkNotNull(session, "The session cannot be null.");
//        Anchor newAnchor = session.hostCloudAnchor(anchor);
        cameraCloudAnchor = session.hostCloudAnchor(cameraAnchor);
        pendingAnchors.put(anchor, new AnchorData(otherData, listener));
    }

    synchronized void putCloudAnchor(Anchor anchor, CloudAnchorListener listener, String otherData) {
        Preconditions.checkNotNull(session, "The session cannot be null.");
        pendingAnchors.put(anchor, new AnchorData(otherData, listener));
    }

    /**
     * This method resolves an anchor. The {@code listener} will be invoked when the results are
     * available.
     */
    synchronized void resolveCloudAnchor(String anchorId, CloudAnchorListener listener) {
        Preconditions.checkNotNull(session, "The session cannot be null.");
        if (lastAnchorId != anchorId) {
            cameraCloudAnchor = session.resolveCloudAnchor(anchorId);
            pendingAnchors.put(cameraCloudAnchor, new AnchorData("", listener));
        }
//        Anchor newAnchor = session.resolveCloudAnchor(anchorId);
    }

    synchronized String generateDistanceOfAnchorToCamera(Anchor startAnchor, Anchor endAnchor) {
        AnchorNode startNode = new AnchorNode(startAnchor);
        AnchorNode endNode = new AnchorNode(endAnchor);
        Vector3 startVect = startNode.getWorldPosition();
        Vector3 endVect = endNode.getWorldPosition();
        final Vector3 difference = Vector3.subtract(startVect, endVect);
        final Quaternion rotate = startNode.getWorldRotation();
//        return difference.x+","+difference.y+","+difference.z+","+rotate.x+","+rotate.y+","+rotate.z+","+rotate.w;
        Pose startPose = startAnchor.getPose();
        Pose endPose = endAnchor.getPose();
        float dx = startPose.tx() - endPose.tx();
        float dy = startPose.ty() - endPose.ty();
        float dz = startPose.tz() - endPose.tz();
//        return dx+","+dy+","+dz+","+rotate.x+","+rotate.y+","+rotate.z+","+rotate.w;
//
//        float qx = startPose.qx() - endPose.qx();
//        float qy = startPose.qy() - endPose.qy();
//        float qz = startPose.qz() - endPose.qz();
//        float qw = startPose.qw() - endPose.qw();
//
        return dx+","+dy+","+dz+","+startPose.qx()+","+endPose.qy()+","+endPose.qz()+","+endPose.qw();

    }

    /** Should be called after a {@link Session#update()} call. */
    synchronized void onUpdate() {
        Preconditions.checkNotNull(session, "The session cannot be null.");
        Iterator<Map.Entry<Anchor, AnchorData>> iter = pendingAnchors.entrySet().iterator();
        while (iter.hasNext() && null != cameraCloudAnchor) {
            Map.Entry<Anchor, AnchorData> entry = iter.next();
            Anchor anchor = entry.getKey();
            if (isReturnableState(cameraCloudAnchor.getCloudAnchorState())) {
                CloudAnchorListener listener = entry.getValue().listener;
                String otherData = entry.getValue().otherData;
                listener.onCloudTaskComplete(generateDistanceOfAnchorToCamera(anchor, cameraCloudAnchor), cameraCloudAnchor, otherData);
                iter.remove();
            }
        }
    }

    /** Used to clear any currently registered listeners, so they wont be called again. */
    synchronized void clearListeners() {
        pendingAnchors.clear();
    }

    private static boolean isReturnableState(CloudAnchorState cloudState) {
        switch (cloudState) {
            case NONE:
            case TASK_IN_PROGRESS:
                return false;
            default:
                return true;
        }
    }
}