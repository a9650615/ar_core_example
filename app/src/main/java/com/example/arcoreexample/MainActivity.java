package com.example.arcoreexample;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.example.arcoreexample.model.ModelLinksManager;
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
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Anchor resolveCameraAnchor;
    private Pose nowCamPose;
    private HostResolveMode currentMode;

    private ViewRenderable imageRenderable;
    private ModelRenderable andyRenderable;
    ModelLinksManager modelLinkManager = new ModelLinksManager();
    private String modelLink = "https://poly.googleusercontent.com/downloads/c/fp/1563760651765431/1dWAYYfUAhn/3MEx9PZHD_W/model.gltf";
    private float scaleRatio = 0.3f;
    private int tempHostCount = 0;
    final private boolean DEBUG = false;

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

//        ModelRenderable.builder()
//                .setSource(this, R.raw.andy)
//                .build()
//                .thenAccept(renderable -> andyRenderable = renderable)
//                .exceptionally(
//                        throwable -> {
//                            Toast toast =
//                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
//                            toast.setGravity(Gravity.CENTER, 0, 0);
//                            toast.show();
//                            return null;
//                        });

        updateModel(true);
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
                cloudAnchorManager.putCloudAnchor(lastAnchor, hostListener, getModelData());
                tempHostCount = 1;
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

    public void updateModel(boolean firstTime) {
        ModelRenderable.builder()
//            .setSource(this, R.raw.andy)
            .setSource(this, RenderableSource.builder().setSource(
                    this,
                    Uri.parse(modelLink),
                    RenderableSource.SourceType.GLTF2
            ).build())
            .build()
            .thenAccept(renderable -> {
                andyRenderable = renderable;
                if (!firstTime) {
                    setNewAnchor(lastAnchor);
                }
            })
            .exceptionally(
                    throwable -> {
                        Toast toast =
                                Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        return null;
                    });
    }

   public void initScene() {
        try {
            Frame frame = mSession.update();
            cloudAnchorManager.onUpdate();
            nowCamPose = frame.getCamera().getPose();
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
//                                makeMessage(Float.toString(closeHitResult.getDistance()));
                                Anchor anchor = closeHitResult.createAnchor();
                                findViewById(R.id.func_btns).setVisibility(View.VISIBLE);
                                setNewAnchor(anchor);
                                isFirstStart = false;
                                // show layout of functions buttons

                                // init camera anchor
                                firstCameraAnchor = mSession.createAnchor(frame.getCamera().getPose());
//                                firstCameraAnchor = anchor;
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

    private void drawLineBetweenTwoAnchor(AnchorNode a1, Anchor a2, int color[]) {
        Vector3 v1 = a1.getWorldPosition();
        Vector3 v2 = new AnchorNode(a2).getWorldPosition();

        final Vector3 difference = Vector3.subtract(v1, v2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(color[0], color[1], color[2]))
                .thenAccept(
                        material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, difference.length()),
                                    Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */
                            Node lineNode = new Node();
                            lineNode.setParent(a1);
                            lineNode.setRenderable(model);
                            lineNode.setWorldPosition(Vector3.add(v1, v2).scaled(.5f));
                            lineNode.setWorldRotation(rotationFromAToB);
                        }
                );
    }

    private void setNewAnchor(Anchor newAnchor, float[] rotate) {
        if (lastAnchor != null) {
            lastAnchor.detach();
        }
        lastAnchor = mSession.createAnchor(newAnchor.getPose().extractTranslation());

        final float[] anchorMatrix = new float[16];
        lastAnchor.getPose().toMatrix(anchorMatrix, 0);
//        makeMessage(anchorMatrix.toString());

        AnchorNode anchorNode = new AnchorNode(lastAnchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        Node node = new Node();
        node.setParent(anchorNode);
        node.setLocalScale(new Vector3(scaleRatio, scaleRatio, scaleRatio));
        float[] cameraRotate = nowCamPose.getRotationQuaternion();
        node.setLocalRotation(new Quaternion(rotate[0], rotate[1], rotate[2], rotate[3]));

//        node.setLocalRotation(Quaternion.axisAngle(new Vector3(-1f, 0, 0), 90f)); // put flat
//            node.setLocalPosition(new Vector3(0f,0f,-1f));
        node.setRenderable(andyRenderable);

        // draw line
        int[] blue = {0, 255, 244};
        int[] red = {235, 64, 52};
        if (DEBUG) {
            drawLineBetweenTwoAnchor(anchorNode, firstCameraAnchor, blue);
            if (null != resolveCameraAnchor) {
                drawLineBetweenTwoAnchor(anchorNode, resolveCameraAnchor, red);
            }
        }
        // draw line
//        tmpAnchor.detach();
        newAnchor.detach();
    }

    private void setNewAnchor(Anchor newAnchor) {
        float[] rotate = {0, 0, 0, 0};
        setNewAnchor(newAnchor, rotate);
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
                (camId, cloudAnchorId, otherData) -> {
                    // if is in host mode not update
                    makeMessage(currentMode.toString());
                    if (tempHostCount == 1) {
                        tempHostCount = 0;
                        return;
                    }
                    if (null == cloudAnchorId) {
                        snackbarHelper.showMessageWithDismiss(this, getString(R.string.no_room_alert));
                        return;
                    }
//                    makeMessage(camId);
                    // When the cloud anchor ID is available from Firebase.
                    cloudAnchorManager.resolveCloudAnchor(
                            camId,
                            (anchor, camera, oth) -> {
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
                                float[] rotate = {0, 0, 0, Float.parseFloat(posStr[6])};
//                                Pose cameraPos = firstCameraAnchor.getPose();
//                                Pose firstCamPose = camera.getPose();
//                                float cameraOffset[] = { cameraPos.tx() - firstCamPose.tx(), cameraPos.ty() - cameraPos.ty(), cameraPos.tz() - cameraPos.tz() };
//                                float camRotate[] = nowCamPose.getRotationQuaternion();
                                Anchor resolveRelativeAnchor = mSession.createAnchor(
                                        camera.getPose().extractTranslation().compose(
                                                Pose
                                                    .makeTranslation(Float.parseFloat(posStr[0]), Float.parseFloat(posStr[1]), Float.parseFloat(posStr[2]))
//                                                    .makeRotation(-camRotate[0], -camRotate[1], -camRotate[2], -camRotate[3])
//                                                    .makeRotation(rotate)
//                                                                .makeTranslation(cameraOffset)
//                                                                .makeRotation(rotate)
                                        )
                                );
                                if (null != resolveCameraAnchor) {
                                    resolveRelativeAnchor.detach();
                                }
                                resolveCameraAnchor = camera;
                                setModelData(otherData);
//                                setNewAnchor(resolveRelativeAnchor);
                                setNewAnchor(resolveRelativeAnchor, rotate);
                            });
                });
    }

    protected String getModelData() {
        return scaleRatio+",";
    }

    protected void setModelData(String data) {
        String[] posStr = data.split(",");
        scaleRatio = Float.parseFloat(posStr[0]);
//        setNewAnchor(lastAnchor);
    }

    public void onCreateRoom(View view) {
        if (hostListener != null) {
            return;
        }
        // host to cloud
        hostListener = new RoomCodeAndCloudAnchorIdListener();
        firebaseManager.getNewRoomCode(hostListener);
        cloudAnchorManager.setCameraAnchor(firstCameraAnchor);
        cloudAnchorManager.hostCloudAnchor(lastAnchor, hostListener, getModelData());// host to online
    }

    public void onJoinRoom(View view) {
        if (isFirstStart) {
            snackbarHelper.showMessageWithDismiss(this, getString(R.string.tracking_not_ready));
            return;
        }
        ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
        dialogFragment.setOkListener(this::onRoomCodeEntered);
        dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
    }

    protected void updateDatabase() {
        if (currentMode == HostResolveMode.HOSTING || currentMode == HostResolveMode.RESOLVING) {
            currentMode = HostResolveMode.HOSTING;
            cloudAnchorManager.clearListeners();
            cloudAnchorManager.putCloudAnchor(lastAnchor, hostListener, getModelData());
        }
    }

    public void onScaleUp(View view) {
        scaleRatio += 0.3f;
        setNewAnchor(lastAnchor);
        updateDatabase();
    }

    public void onChangeModel(View view) {
        String[] keySets = Arrays.copyOf(modelLinkManager.modelList.keySet().toArray(), modelLinkManager.modelList.keySet().toArray().length, String[].class);
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.list_view);
        ListView listView = (ListView) dialog.findViewById(R.id.list_view);
        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                R.layout.text_view, keySets);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(getApplicationContext(), "請等待更新成" + keySets[position], Toast.LENGTH_SHORT).show();
                modelLink = modelLinkManager.modelList.values().toArray()[position].toString();
                updateModel(false);
                dialog.cancel();
            }
        });
        dialog.show();
    }

    public void onScaleDown(View view) {
        if (scaleRatio - 0.3f > 0) {
            scaleRatio -= 0.3f;
            setNewAnchor(lastAnchor);
            updateDatabase();
        }
    }


    private final class RoomCodeAndCloudAnchorIdListener
            implements CloudAnchorManager.CloudAnchorListener, FirebaseManager.RoomCodeListener {

        private Long roomCode;
        private String anchorPos;
        private String cameraAnchorId;
        private String otherData;

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
        public void onCloudTaskComplete(String pos, Anchor cameraAnchor, String othData) {
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
            otherData = othData;
            checkAndMaybeShare();
        }


        private void checkAndMaybeShare() {
            if (roomCode == null || anchorPos == null || cameraAnchorId == null) {
                return;
            }
            firebaseManager.storeAnchorIdInRoom(roomCode, anchorPos, cameraAnchorId, otherData);
            snackbarHelper.showMessageWithDismiss(
                    MainActivity.this, getString(R.string.snackbar_cloud_id_shared) + ":" + roomCode);
        }
    }
}
