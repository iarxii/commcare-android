/**
 * 
 */
package org.commcare.android.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.util.CommCareSession;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.ViewId;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

/**
 * @author ctsims
 *
 */
public class EntityDetailView extends FrameLayout {
    
    private final TextView label;
    private final TextView data;
    private final TextView spacer;
    private final Button callout;
    private final View addressView;
    private final Button addressButton;
    private final TextView addressText;
    private final ImageView imageView;
    private final View calloutView;
    private final Button calloutButton;
    private final TextView calloutText;
    private final ImageButton calloutImageButton;
    private final AspectRatioLayout graphLayout;
    private final Hashtable<Integer, Hashtable<Integer, View>> graphViewsCache;    // index => { orientation => GraphView }
    private final Hashtable<Integer, Intent> graphIntentsCache;    // index => intent
    private final Set<Integer> graphsWithErrors;
    private final ImageButton videoButton;
    private final AudioButton audioButton;
    private final View valuePane;
    private View currentView;
    private final AudioController controller;
    private final LinearLayout detailRow;
    private final LinearLayout.LayoutParams origValue;
    private final LinearLayout.LayoutParams origLabel;
    private final LinearLayout.LayoutParams fill;
    
    private static final String FORM_VIDEO = MediaUtil.FORM_VIDEO;
    private static final String FORM_AUDIO = MediaUtil.FORM_AUDIO;
    private static final String FORM_PHONE = "phone";
    private static final String FORM_ADDRESS = "address";
    private static final String FORM_IMAGE = MediaUtil.FORM_IMAGE;
    private static final String FORM_GRAPH = "graph";
    private static final String FORM_CALLOUT = "callout";

    private static final int TEXT = 0;
    private static final int PHONE = 1;
    private static final int ADDRESS = 2;
    private static final int IMAGE = 3;
    private static final int VIDEO = 4;
    private static final int AUDIO = 5;
    private static final int GRAPH = 6;
    private static final int CALLOUT = 7;
    
    int current = TEXT;

    DetailCalloutListener listener;
    private int oddRowColor;
    private int evenRowColor;

    public EntityDetailView(final Context context, final CommCareSession session, final Detail d, final Entity e, final int index,
            final AudioController controller, final int detailNumber) {
        super(context);
        this.controller = controller;
        
        detailRow = (LinearLayout)View.inflate(context, R.layout.component_entity_detail_item, null);
        label = (TextView)detailRow.findViewById(R.id.detail_type_text);
        spacer = (TextView)detailRow.findViewById(R.id.entity_detail_spacer); 
        data = (TextView)detailRow.findViewById(R.id.detail_value_text);
        currentView = data;
        valuePane = detailRow.findViewById(R.id.detail_value_pane);
        videoButton = (ImageButton)detailRow.findViewById(R.id.detail_video_button);
        
        final ViewId uniqueId = new ViewId(detailNumber, index, true);
        final String audioText = e.getFieldString(index);
        audioButton = new AudioButton(context, audioText, uniqueId, controller, false);
        detailRow.addView(audioButton);
        audioButton.setVisibility(View.GONE);
        
        callout = (Button)detailRow.findViewById(R.id.detail_value_phone);
        //TODO: Still useful?
        //callout.setInputType(InputType.TYPE_CLASS_PHONE);
        addressView = (View)detailRow.findViewById(R.id.detail_address_view);
        addressText = (TextView)addressView.findViewById(R.id.detail_address_text);
        addressButton = (Button)addressView.findViewById(R.id.detail_address_button);
        imageView = (ImageView)detailRow.findViewById(R.id.detail_value_image);
        graphLayout = (AspectRatioLayout)detailRow.findViewById(R.id.graph);
        calloutView = (View)detailRow.findViewById(R.id.callout_view);
        calloutText = (TextView)detailRow.findViewById(R.id.callout_text);
        calloutButton = (Button)detailRow.findViewById(R.id.callout_button);
        calloutImageButton = (ImageButton)detailRow.findViewById(R.id.callout_image_button);
        graphViewsCache = new Hashtable<Integer, Hashtable<Integer, View>>();
        graphsWithErrors = new HashSet<Integer>();
        graphIntentsCache = new Hashtable<Integer, Intent>();
        origLabel = (LinearLayout.LayoutParams)label.getLayoutParams();
        origValue = (LinearLayout.LayoutParams)valuePane.getLayoutParams();

        fill = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        this.addView(detailRow, FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        setParams(session, d, e, index, detailNumber);

        final int[] colorAttr = new int[] {
                R.attr.entity_detail_odd_row_color,
                R.attr.entity_detail_even_row_color
        };
        final Resources.Theme theme = context.getTheme();
        for (int i = 0; i < colorAttr.length; i++) {
            final TypedValue typedValue = new TypedValue();
            theme.resolveAttribute(colorAttr[i], typedValue, true);
            final int color = typedValue.data;
            if(i == 0) { oddRowColor = color; }
            else { evenRowColor = color; }
        }
    }

    public void setLineColor(final boolean isOddRow){
        if(isOddRow){
            detailRow.setBackgroundColor(oddRowColor);
        } else {
            detailRow.setBackgroundColor(evenRowColor);
        }
    }
    
    public void setCallListener(final DetailCalloutListener listener) {
        this.listener = listener;
    }

    public void setParams(final CommCareSession session, final Detail d, final Entity e, final int index, final int detailNumber) {
        final String labelText = d.getFields()[index].getHeader().evaluate();
        label.setText(labelText);
        spacer.setText(labelText);
        
        final Object field = e.getField(index);
        final String textField = e.getFieldString(index);
        boolean veryLong = false;
        final String form = d.getTemplateForms()[index];
        if(FORM_PHONE.equals(form)) {
            callout.setText(textField);
            if(current != PHONE) {
                callout.setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        listener.callRequested(callout.getText().toString());                
                    }
                });
                this.removeView(currentView);
                updateCurrentView(PHONE, callout);
            }
        } else if (FORM_CALLOUT.equals(form) && (field instanceof CalloutData)) {

            final CalloutData callout = (CalloutData) field;

            final String imagePath = callout.getImage();

            if (imagePath != null) {
                // use image as button, if available
                calloutButton.setVisibility(View.GONE);
                calloutText.setVisibility(View.GONE);

                final Bitmap b = ViewUtil.inflateDisplayImage(getContext(), imagePath);

                if (b == null) {
                    calloutImageButton.setImageDrawable(null);
                } else {
                    // Figure out whether our image small or large.
                    if (b.getWidth() > (getScreenWidth() / 2)) {
                        veryLong = true;
                    }

                    calloutImageButton.setPadding(10, 10, 10, 10);
                    calloutImageButton.setAdjustViewBounds(true);
                    calloutImageButton.setImageBitmap(b);
                    calloutImageButton.setId(23422634);
                }

                calloutImageButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        listener.performCallout(callout, CALLOUT);
                    }
                });
            } else {
                calloutImageButton.setVisibility(View.GONE);
                calloutText.setVisibility(View.GONE);

                final String displayName = callout.getDisplayName();
                // use display name if available, otherwise use URI
                if (displayName != null) {
                    calloutButton.setText(displayName);
                } else {
                    final String actionName = callout.getActionName();
                    calloutButton.setText(actionName);
                }

                calloutButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        listener.performCallout(callout, CALLOUT);
                    }
                });
            }

            updateCurrentView(CALLOUT, calloutView);
        }
        else if(FORM_ADDRESS.equals(form)) {
            final String address = textField;
            addressText.setText(address);
            if(current != ADDRESS) {
                addressButton.setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        listener.addressRequested(MediaUtil.getGeoIntentURI(address));
                    }
                });
                updateCurrentView(ADDRESS, addressView);
            }
        } else if(FORM_IMAGE.equals(form)) {
            final String imageLocation = textField;
            final Bitmap b = MediaUtil.getScaledImageFromReference(imageLocation);
            
            if(b == null) {
                imageView.setImageDrawable(null);
            } else {
                //Ok, so. We should figure out whether our image is large or small.
                if(b.getWidth() > (getScreenWidth() / 2)) {
                    veryLong = true;
                }
                
                imageView.setPadding(10, 10, 10, 10);
                imageView.setAdjustViewBounds(true);
                imageView.setImageBitmap(b);
                imageView.setId(23422634);
            }
            
            updateCurrentView(IMAGE, imageView);
        } else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {    // if graph parsing had errors, they'll be stored as a string
            // Fetch graph view from cache, or create it
            View graphView = null;
            final Context context = getContext();
            final int orientation = getResources().getConfiguration().orientation;
            if (graphViewsCache.get(index) != null) {
                graphView = graphViewsCache.get(index).get(orientation);
            }
            else {
                graphViewsCache.put(index, new Hashtable<Integer, View>());
            }
            if (graphView == null) {
                final GraphView g = new GraphView(context, labelText);
                g.setClickable(true);
                try {
                    graphView = g.getView((GraphData) field);
                }
                catch (final InvalidStateException ise) {
                    graphView = new TextView(context);
                    final int padding = (int) context.getResources().getDimension(R.dimen.spacer_small);
                    graphView.setPadding(padding, padding, padding, padding);
                    ((TextView)graphView).setText(ise.getMessage());
                    graphsWithErrors.add(index);
                }
                graphViewsCache.get(index).put(orientation, graphView);
            }
            
            // Fetch full-screen graph intent from cache, or create it
            Intent graphIntent = graphIntentsCache.get(index);
            if (graphIntent == null && !graphsWithErrors.contains(index)) {
                final GraphView g = new GraphView(context, labelText);
                try {
                    graphIntent = g.getIntent((GraphData) field);
                    graphIntentsCache.put(index, graphIntent);
                }
                catch (final InvalidStateException ise) {
                    // This shouldn't happen, since any error should have been caught during getView above
                    graphsWithErrors.add(index);
                }
            }
            final Intent finalIntent = graphIntent;
            
            // Open full-screen graph intent on double tap
            if (!graphsWithErrors.contains(index)) {
                final GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(final MotionEvent e) {
                        return true;
                    }
            
                    @Override
                    public boolean onDoubleTap(final MotionEvent e) {
                        context.startActivity(finalIntent);
                        return true;
                    }
                });
                graphView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(final View view, final MotionEvent event) {
                        return detector.onTouchEvent(event);
                    }
                });
            }
            
            graphLayout.removeAllViews();
            graphLayout.addView(graphView, GraphView.getLayoutParams());

            if (current != GRAPH) {
                // Hide field label and expand value to take up full screen width
                final LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams((ViewGroup.LayoutParams)origValue);
                graphValueLayout.weight = 10;
                valuePane.setLayoutParams(graphValueLayout);

                label.setVisibility(View.GONE);
                data.setVisibility(View.GONE);
                updateCurrentView(GRAPH, graphLayout);
            }
        } else if (FORM_AUDIO.equals(form)) {
            final ViewId uniqueId = new ViewId(detailNumber, index, true);
            audioButton.modifyButtonForNewView(uniqueId, textField, true);
            updateCurrentView(AUDIO, audioButton);
        } else if(FORM_VIDEO.equals(form)) { //TODO: Why is this given a special string?
            final String videoLocation = textField;
            String localLocation = null;
            try{ 
                localLocation = ReferenceManager._().DeriveReference(videoLocation).getLocalURI();
                if(localLocation.startsWith("/")) {
                    //TODO: This should likely actually be happening with the getLocalURI _anyway_.
                    localLocation = FileUtil.getGlobalStringUri(localLocation);
                }
            } catch(final InvalidReferenceException ire) {
                Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Couldn't understand video reference format: " + localLocation + ". Error: " + ire.getMessage());
            }
            
            final String location = localLocation;
            
            videoButton.setOnClickListener(new OnClickListener() {

                public void onClick(final View v) {
                    listener.playVideo(location);    
                }
                
            });
            
            if(location == null) {
                videoButton.setEnabled(false);
                Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "No local video reference available for ref: " + videoLocation);
            } else {
                videoButton.setEnabled(true);
            }
            
            updateCurrentView(VIDEO, videoButton);
        } else {
            final String text = textField;
            data.setText((text));
            if(text != null && text.length() > this.getContext().getResources().getInteger(R.integer.detail_size_cutoff)) {
                veryLong = true;
            }

            updateCurrentView(TEXT, data);
        }
        
        if(veryLong) {
            detailRow.setOrientation(LinearLayout.VERTICAL);
            spacer.setVisibility(View.GONE);
            label.setLayoutParams(fill);
            valuePane.setLayoutParams(fill);
        } else {
            if(detailRow.getOrientation() != LinearLayout.HORIZONTAL) {
                detailRow.setOrientation(LinearLayout.HORIZONTAL);
                spacer.setVisibility(View.INVISIBLE);
                label.setLayoutParams(origLabel);
                valuePane.setLayoutParams(origValue);
            }
        }
    }
    
    /*
     * Appropriately set current & currentView.
     */
    private void updateCurrentView(final int newCurrent, final View newView) {
        if (newCurrent != current) {
            currentView.setVisibility(View.GONE);
            newView.setVisibility(View.VISIBLE);
            currentView = newView;
            current = newCurrent;
        }
        
        if (current != GRAPH) {
            label.setVisibility(View.VISIBLE);
            final LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams((ViewGroup.LayoutParams)origValue);
            graphValueLayout.weight = 10;
            valuePane.setLayoutParams(origValue);
        }
    }
    
    /*
     * Get current device screen width
     */
    private int getScreenWidth() {
        final Display display = ((WindowManager) this.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getWidth();
    }
    
}
