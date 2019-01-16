package com.example.ieshdixit.show;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity

{

    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;
    private static final String TAG = "MainActivity:";
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private DatabaseReference mDatabaseRef;

    private static final int PICK_IMAGE_REQUEST = 1;

    // Our background thread, which does all of the heavy lifting so we don't block the main thread.
    private HandlerThread mBackgroundThread;

    // Handler for the background thread, to which we post background thread tasks.
    private Handler mBackgroundThreadHandler;

    // The asset ID to download and display.
    private static final String ASSET_ID = "6b7Ul6MeLrJ";

    // Attributions text to display for the object (title and author).
    private String mAttributionText = "";

    private String finallinktogltf;

    private ImageView mImageView;
    private Uri mImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
        });

        // Create a background thread, where we will do the heavy lifting.
        mBackgroundThread = new HandlerThread("Worker");
        mBackgroundThread.start();
        mBackgroundThreadHandler = new Handler(mBackgroundThread.getLooper());

        // Request the asset from the Poly API.
        Log.d(TAG, "Requesting asset "+ ASSET_ID);
        PolyAPI.GetAsset(ASSET_ID, mBackgroundThreadHandler, new AsyncHttpRequest.CompletionListener() {
            @Override
            public void onHttpRequestSuccess(byte[] responseBody) {
                // Successfully fetched asset information. This does NOT include the model's geometry,
                // it's just the metadata. Let's parse it.
                finallinktogltf = getGLTFurl(responseBody);
                Log.e(TAG, "finallinktogltf=  "+ finallinktogltf);
                sendMessage(finallinktogltf);
            }
            @Override
            public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                // Something went wrong with the request.
                handleRequestFailure(statusCode, message, exception);
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(mreceiver, new IntentFilter("linkfromthread"));
    }

    BroadcastReceiver mreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String link = intent.getStringExtra("msg");
            Log.d("receiver", "Got link: " + link);
            initializeGallery(link);
        }
    };

    private void sendMessage(String finallinktogltf2) {
        Log.d("sender", "Broadcasting link");
        Intent linkfromthreadintent = new Intent("linkfromthread");
        // You can also include some extra data.
        linkfromthreadintent.putExtra("msg",finallinktogltf2);
        LocalBroadcastManager.getInstance(this).sendBroadcast(linkfromthreadintent);
    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);

            }
            else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }
        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame(); //getArFrame() returns the most recent ARCore Frame if it is available
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && //instanceof is a java operator
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    private void initializeGallery(String linktogltf) {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

//        ImageView zigzag = new ImageView(this);
//        zigzag.setImageResource(R.drawable.zigzag);
//        zigzag.setContentDescription("zigzag");
//        zigzag.setOnClickListener(view -> {
//            Log.i(TAG, "LINK: "+ linktogltf);
//                addObject(Uri.parse(linktogltf));
//        });
//        gallery.addView(zigzag);

        ImageView oc = new ImageView(this);
        oc.setImageResource(R.drawable.orangecloud);
        oc.setContentDescription("orangecloud");
        oc.setOnClickListener(view -> {
            //Log.i(TAG, "LINK: "+ linktogltf);
            openFileChooser();
        });
        gallery.addView(oc);
    }

    private void addObject(Uri model) {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    placeObject(fragment, hit.createAnchor(), model);
                    break;

                }
            }
        }
    }

    private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
        CompletableFuture<Void> renderableFuture =
                ModelRenderable.builder()
                        .setSource(fragment.getContext(),
                                RenderableSource.builder().setSource(fragment.getContext(),
                                        model, RenderableSource.SourceType.GLTF2)
                                        .setScale(0.1f).build())
                        .setRegistryId(model)
                        .build()
                        .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                        .exceptionally((throwable -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setMessage(throwable.getMessage())
                                    .setTitle("Codelab error!");
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return null;
                        }));
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        //node.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 270));
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }

    // NOTE: this runs on the background thread.
    private void handleRequestFailure(int statusCode, String message, Exception exception) {
        // NOTE: because this is a simple sample, we don't have any real error handling logic
        // other than just printing the error. In an actual app, this is where you would take
        // appropriate action according to your app's use case. You could, for example, surface
        // the error to the user or retry the request later.
        Log.e(TAG, "Request failed. Status code " + statusCode + ", message: " + message +
                ((exception != null) ? ", exception: " + exception : ""));
        if (exception != null) exception.printStackTrace();
    }



//    private void parseAsset(byte[] assetData) {
//        //String str = null;
//        Log.d(TAG, "Got asset response (" + assetData.length + " bytes). Parsing.");
//        String assetBody = new String(assetData, Charset.forName("UTF-8"));
//        Log.d(TAG, assetBody);
//        try {
//            JSONObject response = new JSONObject(assetBody);
//            String displayName = response.getString("displayName");
//            String authorName = response.getString("authorName");
//            Log.d(TAG, "Display name: " + displayName);
//            Log.d(TAG, "Author name: " + authorName);
//            mAttributionText = displayName + " by " + authorName;
//
//            JSONArray formats = response.getJSONArray("formats");
//            boolean foundObjFormat = false;
//            for (int i = 0; i < formats.length(); i++) {
//                JSONObject format = formats.getJSONObject(i);
//                if (format.getString("formatType").equals("GLTF2")) {
//                    // Found the GLTF2 format. The format gives us the URL of the data files that we should
//                    // download (which include the GLTF2 file, the bin file and the textures). We will now
//                    // request those files.
//                    JSONObject rootFile = format.getJSONObject("root");
//                    String url = rootFile.getString("url");
//                    foundObjFormat = true;
//                    Log.e(TAG, "URL= "+url);
//                    break;
//                }
//            }
//            if (!foundObjFormat) {
//                // If this happens, it's because the asset doesn't have a representation in the GLTF2
//                // format. Since this simple sample code can only parse GLTF2, we can't proceed.
//                // But other formats might be available, so if your client supports multiple formats,
//                // you could still try a different format instead.
//                Log.e(TAG, "Could not find GLTF2 format in asset.");
//            }
//            return new String rootFile.getString("url");
//        } catch (JSONException jsonException) {
//            Log.e(TAG, "JSON parsing error while processing response: " + jsonException);
//            jsonException.printStackTrace();
//        }
//
//    }

    private String getGLTFurl(byte[] assetData) {
        Log.d(TAG, "Got asset response (" + assetData.length + " bytes). Parsing....");
        String assetBody = new String(assetData, Charset.forName("UTF-8"));
        Log.d(TAG, assetBody);
        String url = new String();
        try {
            JSONObject response = new JSONObject(assetBody);
            JSONArray formats = response.getJSONArray("formats");
            boolean foundgltfFormat = false;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                if (format.getString("formatType").equals("GLTF2")) {
                    JSONObject rootFile = format.getJSONObject("root");
                    url = rootFile.getString("url");
                    foundgltfFormat = true;
                    break;
                }
            }
            if (!foundgltfFormat) {
                Log.e(TAG, "Could not find GLTF2 format in asset.");
            }
        } catch (JSONException jsonException) {
            Log.e(TAG, "JSON parsing error while processing response: " + jsonException);
            jsonException.printStackTrace();
        }
        Log.e(TAG, "URL= " + url);
        return url;
    }

    private void addimgObject(View v) {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    placeimgObject(fragment, hit.createAnchor(), v);
                    break;
                }
            }
        }
    }

    private void placeimgObject(ArFragment fragment, Anchor anchor, View v) {
        CompletableFuture<Void> renderableFuture =
                ViewRenderable.builder()
                        .setView(this, v)
                        .build()
                        .thenAccept(renderable -> add2dNodeToScene(fragment,anchor,renderable))
                        .exceptionally((throwable -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setMessage(throwable.getMessage())
                                    .setTitle("Codelab error!");
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return null;
                        }));
    }

    private void add2dNodeToScene(ArFragment fragment, Anchor anchor, ViewRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());

        node.setRenderable(renderable);
        ImageView imageview = (ImageView) renderable.getView();
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);

        node.select();
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    public void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            mImageUri= data.getData();
            LayoutInflater l = getLayoutInflater();
            View v = l.inflate(R.layout.test, null);
            mImageView = (ImageView) v.findViewById(R.id.image_view);
            Glide.with(this).load(mImageUri).into(mImageView);
            addimgObject(v);
        }
    }


//    public void opendialog() {
//
//        customContent dialog = new customContent();
//        dialog.show(getSupportFragmentManager(), "Custom content dialog");
//    }
//
//        @Override
//        public void applycontent(String customcontent) {
//            LayoutInflater l = getLayoutInflater();
//            View v = l.inflate(R.layout.test, null);
//            TextView newcontent = (TextView) v.findViewById(R.id.InfoCard);
//            Log.i(TAG, "new content= " + newcontent);
//            newcontent.setText(customcontent);
//            addObject(v);
//        }

}
