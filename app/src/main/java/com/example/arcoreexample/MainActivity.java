package com.example.arcoreexample;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
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
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.EnumSet;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    protected boolean ar_core_enabled = false;
    protected boolean mUserRequestedInstall = true;
    protected boolean isFirstStart = true;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private Session mSession;
    private ArFragment arFragment;

    private ViewRenderable imageRenderable;
    private ModelRenderable andyRenderable;

    private  Display display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        display = getWindowManager().getDefaultDisplay();
        checkArCoreIsEnabled();
        if( !checkIsSupportedDeviceOrFinish(this) ) {
            return;
        }
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
            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            Node node = new Node();
            node.setParent(anchorNode);
            node.setLocalScale(new Vector3(0.3f, 0.3f, 1f));
            node.setLocalRotation(Quaternion.axisAngle(new Vector3(-1f, 0, 0), 90f)); // put flat
//            node.setLocalPosition(new Vector3(0f,0f,-1f));
            node.setRenderable(imageRenderable);
        });
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
                                AnchorNode anchorNode = new AnchorNode(anchor);
                                anchorNode.setParent(arFragment.getArSceneView().getScene());

                                Node node = new Node();
                                node.setParent(anchorNode);
                                node.setLocalScale(new Vector3(0.3f, 0.3f, 1f));
                                node.setLocalRotation(Quaternion.axisAngle(new Vector3(-1f, 0, 0), 90f)); // put flat
//            node.setLocalPosition(new Vector3(0f,0f,-1f));
                                node.setRenderable(imageRenderable);

                                isFirstStart = false;
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
        mSession.configure(config);
        arFragment.getArSceneView().setupSession(mSession);
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
}
