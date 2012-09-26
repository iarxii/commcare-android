/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.view.TextImageAudioView;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

/**
 * Adapter class to handle both Menu and Entry items
 * @author wspride
 *
 */
public class GenericMenuListAdapter implements ListAdapter {
	
	private CommCareSession session;
	private CommCarePlatform platform;
	private Context context;
	private Object[] objectData;
	
	public GenericMenuListAdapter(Context context, CommCarePlatform platform, String menuID){
		
		this.platform = platform;
		this.context = context;
		
		Vector<Object> items = new Vector<Object>();
		
		Hashtable<String, Entry> map = platform.getMenuMap();
		
		for(Suite s : platform.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
	    		if(m.getId().equals(menuID)) {
	    			for(String command : m.getCommandIds()) {
	    				XPathExpression mRelevantCondition = m.getRelevantCondition(m.indexOfCommand(command));
	    				if(mRelevantCondition != null) {	    					
		    				session = CommCareApplication._().getCurrentSession();
		    				EvaluationContext mEC = session.getEvaluationContext(getInstanceInit());
		    				Object ret = mRelevantCondition.eval(mEC);
		    				try {
		    					if(!XPathFuncExpr.toBoolean(ret)) {
		    						continue;
		    					}
		    				} catch(XPathTypeMismatchException e) {
		    					Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "relevancy condition for menu item returned non-boolean value : " + ret);
		    					throw new RuntimeException("relevancy condition for menu item returned non-boolean value : " + ret);
		    					
		    				}
	    				if(!(Boolean)mRelevantCondition.eval(mEC)) { continue;}
	    				}
	    				
    					Entry e = map.get(command);
    					items.add(e);
	    			}
					continue;
	    		}
				if(menuID.equals(m.getRoot())){
					items.add(m);
				}
			}
		}
		
		objectData = new Object[items.size()];
		items.copyInto(objectData);
	}
	
	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#areAllItemsEnabled()
	 */
	public boolean areAllItemsEnabled() {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#isEnabled(int)
	 */
	public boolean isEnabled(int arg0) {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	public int getCount() {
		return (objectData.length);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		return objectData[i];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {

		Object tempItem = objectData[i];
		
		if(tempItem instanceof Menu){
			return ((Menu)tempItem).getId().hashCode();
		}
		else{
			return ((Entry)tempItem).getCommandId().hashCode();
		}
	}


	/*
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	public int getItemViewType(int i) {
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int i, View v, ViewGroup vg) {
		System.out.println("98 getting view in generic");
		Object mObject = objectData[i];
		TextImageAudioView emv = (TextImageAudioView)v;
		TextView mQuestionText = textViewHelper(mObject);
		if(emv == null) {
			emv = new TextImageAudioView(context);
			emv.setAVT(mQuestionText, getAudioURI(mObject), getImageURI(mObject));
		} else{
			emv.setAVT(mQuestionText, getAudioURI(mObject), getImageURI(mObject));
		}
		return emv;
	}
	
	private CommCareInstanceInitializer getInstanceInit() {
    	return new CommCareInstanceInitializer(session);
    }
	
	/*
	 * Helpers to make the getView call Entry/Menu agnostic
	 */
	
	public String getAudioURI(Object e){
		if(e instanceof Menu){
			return ((Menu)e).getAudioURI();
		}
		return ((Entry)e).getAudioURI();
	}
	
	public String getImageURI(Object e){
		if(e instanceof Menu){
			return ((Menu)e).getImageURI();
		}
		return ((Entry)e).getImageURI();
	}
	
	/*
	 * Helper to build the TextView for the TextImageAudioView constructor
	 */
	public TextView textViewHelper(Object e){
		TextView mQuestionText = new TextView(context);
		if(e instanceof Menu){
			mQuestionText.setText((CharSequence) ((Menu)e).getName().evaluate());
		}
		else{
			mQuestionText.setText((CharSequence) ((Entry)e).getText().evaluate());
		}
	    mQuestionText.setTypeface(null, Typeface.BOLD);
	    mQuestionText.setPadding(0, 0, 0, 7);
	    mQuestionText.setId((int)Math.random()*100000000); // assign random id
	    mQuestionText.setHorizontallyScrolling(false);
	    return mQuestionText;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getViewTypeCount()
	 */
	public int getViewTypeCount() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#hasStableIds()
	 */
	public boolean hasStableIds() {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#isEmpty()
	 */
	public boolean isEmpty() {
		return false;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
	 */
	public void registerDataSetObserver(DataSetObserver arg0) {

	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	public void unregisterDataSetObserver(DataSetObserver arg0) {

	}
}