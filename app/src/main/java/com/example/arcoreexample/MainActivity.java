package com.example.arcoreexample;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private enum HostResolveMode {
        NONE,
        HOSTING,
        RESOLVING,
    }

    private static final String TAG = MainActivity.class.getSimpleName();
    protected boolean ar_core_enabled = false;
    protected boolean mUserRequestedInstall = true;
    protected boolean isFirstStart = true;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private Session mSession;
    private ArFragment arFragment;

    // cloud anchor
    private final CloudAnchorManager cloudAnchorManager = new CloudAnchorManager();
    private FirebaseManager firebaseManager;
    private final SnackbarHelper snackbarHelper = new SnackbarHelper();
    private RoomCodeAndCloudAnchorIdListener hostListener;
    private Anchor lastAnchor;
    private Anchor firstCameraAnchor;
    private HostResolveMode currentMode;

    private ViewRenderable imageRenderable;
    private ModelRenderable andyRenderable;

    private Display display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        display = getWindowManager().getDefaultDisplay();
        checkArCoreIsEnabled();
        if( !checkIsSupportedDeviceOrFinish(this) ) {
            return;
        }
        firebaseManager = new FirebaseManager(this);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
//        arFragment.getArSceneView().getPlaneRenderer().setEnabled(false);
//        arFragment.getArSceneView().getScene().setOnUpdateListener(this::onUpdate);
        arFragment.getArSceneView().getScene().addOnUpdateListener(new Scene.OnUpdateListener() {
            @Override
            public void onUpdate(FrameTime frameTime) {
                initScene();
            }
        });

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        ViewRenderable.builder()
                .setView(arFragment.getContext(), R.layout.test_render_view)
                .build()
                .thenAccept(renderable -> {
                    imageRenderable = renderable;
//                    ImageView imgView = (ImageView)renderable.getView();
                });

        arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
            float[] pos = { 0,0,-1 };
            float[] rotation = {0,0,0,1};

            setNewAnchor(hitResult.createAnchor());
            if (currentMode == HostResolveMode.HOSTING || currentMode == HostResolveMode.RESOLVING) {
                currentMode = HostResolveMode.HOSTING;
                cloudAnchorManager.clearListeners();
                cloudAnchorManager.putCloudAnchor(lastAnchor, hostListener);
//                cloudAnchorManager.hostCloudAnchor(lastAnchor, hostListener);
            }
        });

        currentMode = HostResolveMode.NONE;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Make sure ARCore is installed and up to date.
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success, create the AR session.
                        initArSession();
                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        // need the newest version of ar core
                        mUserRequestedInstall = false;
                        return;
                }
            } else {
                mSession.resume();
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        catch (UnavailableDeviceNotCompatibleException e) { return; }
        catch (UnavailableApkTooOldException e) { return; }
        catch (UnavailableArcoreNotInstalledException e) { return; }
        catch (UnavailableSdkTooOldException e) { return; }
        catch (CameraNotAvailableException e) { return; }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSession != null) {
            mSession.pause();
        }
    }

   public void initScene() {
        try {
            Frame frame = mSession.update();
            cloudAnchorManager.onUpdate();
            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                if (plane.getTrackingState() == TrackingState.TRACKING) {
                    // detect has finished
                    if (frame.getCamera().getTrackingState() == TrackingState.TRACKING) { // is tracking now
                        // place anchor
                        if (isFirstStart) {
                            float[] rotation = { 0, 0, 0, 1};
                            Point size = new Point();
                            display.getSize(size);
                            MotionEvent tap = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, size.x/2 , size.y/2 , 0);
                            if (frame.hitTest(tap).size() > 0) {
                                makeMessage("tracking finish");
                                HitResult closeHitResult = getClosestHit(frame.hitTest(tap));
                                makeMessage(Float.toString(closeHitResult.getDistance()));
                                Anchor anchor = closeHitResult.createAnchor();

                                setNewAnchor(anchor);
                                isFirstStart = false;

                                // init camera anchor
//                                firstCameraAnchor = mSession.createAnchor(frame.getCamera().getPose());
                                firstCameraAnchor = anchor;
                            } else {
                                // still not track finished
                                return;
                            }
                        }
                    }
                }
            }
        } catch (CameraNotAvailableException e) { return; }
    }

    private HitResult getClosestHit(List<HitResult> hitResults) {

        for (HitResult hitResult : hitResults) {

            if (hitResult.getTrackable() instanceof Plane) {

                return hitResult;
            }
        }

        return  hitResults.get(0);
    }

    void makeMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    void initArSession() throws UnavailableApkTooOldException, UnavailableArcoreNotInstalledException, UnavailableSdkTooOldException, UnavailableDeviceNotCompatibleException {
        mSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
        Config config = new Config(mSession);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
        mSession.configure(config);
        arFragment.getArSceneView().setupSession(mSession);
        cloudAnchorManager.setSession(mSession);
    }


    void checkArCoreIsEnabled() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkArCoreIsEnabled();
                }
            }, 300);
        }
        if (availability.isSupported()) {
            ar_core_enabled = true;
        } else {
            // Unsupported or unknown.
        }
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private void setNewAnchor(Anchor newAnchor) {
        if (lastAnchor != null) {
            lastAnchor.detach();
        }
        lastAnchor = newAnchor;
        AnchorNode anchorNode = new AnchorNode(lastAnchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(anchorNode);
        node.setLocalScale(new Vector3(0.3f, 0.3f, 1f));
        node.setLocalRotation(Quaternion.axisAngle(new Vector3(-1f, 0, 0), 90f)); // put flat
//            node.setLocalPosition(new Vector3(0f,0f,-1f));
        node.setRenderable(imageRenderable);
    }

    private void onRoomCodeEntered(Long roomCode) {
        currentMode = HostResolveMode.RESOLVING;
        hostListener = new RoomCodeAndCloudAnchorIdListener();
        hostListener.roomCode = roomCode;
//        hostButton.setEnabled(false);
//        resolveButton.setText(R.string.cancel);
//        roomCodeText.setText(String.valueOf(roomCode));
        snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

        // Register a new listener for the given room.
        firebaseManager.registerNewListenerForRoom(
                roomCode,
                (camId, cloudAnchorId) -> {
                    makeMessage(camId);
                    // When the cloud anchor ID is available from Firebase.
                    cloudAnchorManager.resolveCloudAnchor(
                            camId,
                            (anchor, camera) -> {
                                // When the anchor has been resolved, or had a final error state.
                                Anchor.CloudAnchorState cloudState = camera.getCloudAnchorState();
                                if (cloudState.isError()) {
                                    Log.w(
                                            TAG,
                                            "The anchor in room "
                                                    + roomCode
                                                    + " could not be resolved. The error state was "
                                                    + cloudState);
                                    snackbarHelper.showMessageWithDismiss(
                                            MainActivity.this,
                                            getString(R.string.snackbar_resolve_error) + cloudState);
                                    return;
                                }
                                snackbarHelper.showMessageWithDismiss(
                                        MainActivity.this, getString(R.string.snackbar_resolve_success));
                                String[] posStr = cloudAnchorId.split(",");
                                float[] pos = {Float.parseFloat(posStr[0]), Float.parseFloat(posStr[1]), Float.parseFloat(posStr[2])};
                                setNewAnchor(mSession.createAnchor(camera.getPose().compose(Pose.makeTranslation(pos)))); //
                            });
                });
    }

    public void onCreateRoom(View view) {
        if (hostListener != null) {
            return;
        }
        // host to cloud
        hostListener = new RoomCodeAndCloudAnchorIdListener();
        firebaseManager.getNewRoomCode(hostListener);
        cloudAnchorManager.setCameraAnchor(firstCameraAnchor);
        cloudAnchorManager.hostCloudAnchor(lastAnchor, hostListener);// host to online
    }

    public void onJoinRoom(View view) {
        ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
        dialogFragment.setOkListener(this::onRoomCodeEntered);
        dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
    }


    private final class RoomCodeAndCloudAnchorIdListener
            implements CloudAnchorManager.CloudAnchorListener, FirebaseManager.RoomCodeListener {

        private Long roomCode;
        private String anchorPos;
        private String cameraAnchorId;

        @Override
        public void onNewRoomCode(Long newRoomCode) {
            Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
            roomCode = newRoomCode;
//            roomCodeText.setText(String.valueOf(roomCode));
            snackbarHelper.showMessageWithDismiss(
                    MainActivity.this, getString(R.string.snackbar_room_code_available));
            checkAndMaybeShare();
            currentMode = HostResolveMode.HOSTING;
//            synchronized (singleTapLock) {
//                // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
//                // is tapped), to prevent an anchor being placed before we know the room code and able to
//                // share the anchor ID.
//                currentMode = HostResolveMode.HOSTING;
//            }
        }

        @Override
        public void onError(DatabaseError error) {
            Log.w(TAG, "A Firebase database error happened.", error.toException());
            snackbarHelper.showError(
                    MainActivity.this, getString(R.string.snackbar_firebase_error));
        }

        @Override
        public void onCloudTaskComplete(String pos, Anchor cameraAnchor) {
            Anchor.CloudAnchorState cloudState = cameraAnchor.getCloudAnchorState();
            if (cloudState.isError()) {
                Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
                makeMessage("Error hosting a cloud anchor, state " + cloudState);
//                snackbarHelper.showMessageWithDismiss(
//                        this, getString(R.string.snackbar_host_error, cloudState));
                return;
            }
//            Preconditions.checkState(
//                    cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
//            cloudAnchorId = anchor.getCloudAnchorId();
            anchorPos = pos;
            cameraAnchorId = cameraAnchor.getCloudAnchorId();
            checkAndMaybeShare();
        }


        private void checkAndMaybeShare() {
            if (roomCode == null || anchorPos == null || cameraAnchorId == null) {
                return;
            }
            firebaseManager.storeAnchorIdInRoom(roomCode, anchorPos, cameraAnchorId);
            snackbarHelper.showMessageWithDismiss(
                    MainActivity.this, getString(R.string.snackbar_cloud_id_shared));
        }
    }
}
