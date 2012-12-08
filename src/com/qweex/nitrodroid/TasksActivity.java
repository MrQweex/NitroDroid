/*
Copyright (c) 2012 Qweex
Copyright (c) 2012 Jon Petraglia

This software is provided 'as-is', without any express or implied warranty. In no event will the authors be held liable for any damages arising from the use of this software.

Permission is granted to anyone to use this software for any purpose, including commercial applications, and to alter it and redistribute it freely, subject to the following restrictions:

    1. The origin of this software must not be misrepresented; you must not claim that you wrote the original software. If you use this software in a product, an acknowledgment in the product documentation would be appreciated but is not required.

    2. Altered source versions must be plainly marked as such, and must not be misrepresented as being the original software.

    3. This notice may not be removed or altered from any source distribution.
 */
package com.qweex.nitrodroid;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

public class TasksActivity
{
	private static final int ID_MAGIC     = 1;
	private static final int ID_HAND   = 2;
	private static final int ID_TITLE = 3;
	private static final int ID_DATE   = 4;
	private static final int ID_PRIORITY  = 5;	
	
	ListView lv;
	boolean allTasks = false;
	static View lastClicked = null;
	View editingTags = null;
	public static String lastClickedID;
	View separator;
	TextView tag_bubble;
	String listName;
	public String listHash;
	ArrayList<String> tasksContents;
	public Activity context;
	QuickAction sortPopup;
	Drawable selectedDrawable, normalDrawable;
	AlertDialog.Builder deleteDialog;
	
	//@Overload
	public void onCreate(Bundle savedInstanceState)
	{
		//super.onCreate(savedInstanceState)
		//context.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sortPopup = new QuickAction(context, QuickAction.VERTICAL);
		
        sortPopup.addActionItem(new ActionItem(ID_MAGIC, context.getResources().getString(R.string.magic), context.getResources().getDrawable(R.drawable.magic)));
        sortPopup.addActionItem(new ActionItem(ID_HAND, context.getResources().getString(R.string.hand), context.getResources().getDrawable(R.drawable.hand)));
        sortPopup.addActionItem(new ActionItem(ID_TITLE, context.getResources().getString(R.string.title), createTitleDrawable()));
        sortPopup.addActionItem(new ActionItem(ID_DATE, context.getResources().getString(R.string.date), context.getResources().getDrawable(R.drawable.date)));
        sortPopup.addActionItem(new ActionItem(ID_PRIORITY, context.getResources().getString(R.string.priority), context.getResources().getDrawable(R.drawable.priority)));
        sortPopup.setOnActionItemClickListener(selectSort);
        
        TypedArray a = context.getTheme().obtainStyledAttributes(ListsActivity.themeID, new int[] {R.attr.tasks_selector});     
        int attributeResourceId = a.getResourceId(0, 0);
        normalDrawable = context.getResources().getDrawable(attributeResourceId);
        selectedDrawable = context.getResources().getDrawable(R.drawable.low_button);
        
        deleteDialog = new AlertDialog.Builder(context);
		deleteDialog.setTitle(R.string.warning);
		deleteDialog.setMessage(R.string.delete_task);
		deleteDialog.setPositiveButton(R.string.yes, confirmDelete);
		deleteDialog.setNegativeButton(R.string.no, confirmDelete);
		
        doCreateStuff();
	}
	
	
	public Drawable createTitleDrawable()
	{
		final int DIM = 24;

		Bitmap canvasBitmap = Bitmap.createBitmap(DIM, 
		                                          DIM, 
		                                          Bitmap.Config.ARGB_8888);
		Canvas imageCanvas = new Canvas(canvasBitmap);
		
		Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		imagePaint.setTypeface(Typeface.SERIF);
		imagePaint.setTextAlign(Align.CENTER);
		imagePaint.setTextSize(30f);
		imagePaint.setAntiAlias(true);
		imagePaint.setColor(0xffffffff);

		imageCanvas.drawText("A", 
		                         DIM / 2, 
		                         DIM, 
		                         imagePaint); 
		BitmapDrawable finalImage = new BitmapDrawable(canvasBitmap);
		
		return finalImage.getCurrent();
	}
	
	
	public void doCreateStuff()
	{
		//context.setTheme(ListsActivity.themeID);
		//context.setContentView(R.layout.tasks);
		
		ImageButton sortButton = ((ImageButton)context.findViewById(R.id.sortbutton));
        sortButton.setOnClickListener(new OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			sortPopup.show(v);
    		}
        });
        ((ImageButton)context.findViewById(R.id.addbutton)).setOnClickListener(clickAdd);
        ((ImageButton)context.findViewById(R.id.deletebutton)).setOnClickListener(clickDelete);
        
		lv = (ListView) ((Activity) context).findViewById(R.id.tasksListView);
		lv.setEmptyView(context.findViewById(R.id.empty2));
		((TextView)context.findViewById(R.id.taskTitlebar)).setText(listName);		
		lv.setOnItemClickListener(selectTask);
		createTheAdapterYouSillyGoose();
		
	}
	
	void createTheAdapterYouSillyGoose()
	{
		Cursor r;
		System.out.println("Eh Listhasho = " + listHash);
		if(listHash==null)
			return;
		if(listHash.equals("all"))			//All
			listHash = null;
		else if(listHash.equals("today"))		//Today
		{
			listHash = null;
			System.out.println("Time: " + getBeginningOfDayInSeconds());
			r = ListsActivity.syncHelper.db.getTodayTasks(getBeginningOfDayInSeconds());
			lv.setAdapter(new TaskAdapter(context, R.layout.task_item, r));
			return;
		}
		
		r = ListsActivity.syncHelper.db.getTasksOfList(listHash, "order_num");
        lv.setAdapter(new TaskAdapter(context, R.layout.task_item, r));
	}
	
	
	OnClickListener clickAdd = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			if(listHash.equals("logbook") || listHash.equals("today") || listHash.equals("next"))
			{
				Toast.makeText(v.getContext(), R.string.long_winded_reprimand, Toast.LENGTH_LONG).show();
				return;
			}
			
			lastClickedID = getID();
			
			ListsActivity.syncHelper.db.open();
			int order = ListsActivity.syncHelper.db.getTasksOfList(listHash, "order_num").getCount();
			ListsActivity.syncHelper.db.open();
			ListsActivity.syncHelper.db.insertTask(lastClickedID, v.getContext().getResources().getString(R.string.default_task),
					0, 0, "", listHash, 0, "", order);
			ListsActivity.syncHelper.db.close();
			
			System.out.println("Urg. new id = " + lastClickedID);
			createTheAdapterYouSillyGoose();
			System.out.println("Urg. new id ~ " + lastClickedID);
			
			try {
				Method func = ListView.class.getMethod("smoothScrollToPosition", Integer.TYPE);
				func.invoke(lv, lv.getCount() - 1);
			}catch(Exception e)
			{
				lv.setSelection(lv.getCount() - 1);
			}
			
			TextView currentListCount = (TextView)ListsActivity.currentList.findViewById(R.id.listNumber);
			int i = Integer.parseInt((String) currentListCount.getText()) + 1;
			currentListCount.setText(Integer.toString(i));
			
		}
    };
    
    OnClickListener clickDelete = new OnClickListener()
	{
    	@Override
		public void onClick(View v)
		{
    		if(lastClicked==null)
    			return;
    		TasksActivity.this.deleteDialog.show();
		}
	};
	
	DialogInterface.OnClickListener confirmDelete = new DialogInterface.OnClickListener() {
	    @Override
	    public void onClick(DialogInterface dialog, int which) {
	        switch (which){
	        case DialogInterface.BUTTON_POSITIVE:
	            ListsActivity.syncHelper.db.deleteTask(lastClickedID);
	            createTheAdapterYouSillyGoose();
	            lastClicked = null;
	            lastClickedID = null;
	            
	            TextView currentListCount = (TextView)ListsActivity.currentList.findViewById(R.id.listNumber);
				int i = Integer.parseInt((String) currentListCount.getText()) - 1;
				currentListCount.setText(Integer.toString(i));
	            break;

	        case DialogInterface.BUTTON_NEGATIVE:
	            //No button clicked
	            break;
	        }
	    }
	};
	
	public static long getBeginningOfDayInSeconds()
	{
		java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getDefault());
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}
	
    
	QuickAction.OnActionItemClickListener selectSort = new QuickAction.OnActionItemClickListener() {			
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId) {				
			//ActionItem actionItem = sortPopup.getActionItem(pos);
             
			Cursor r;
			switch(actionId)
			{
			case ID_MAGIC:
				ArrayList<taskObject> nert = new ArrayList<taskObject>();
				r = ListsActivity.syncHelper.db.getTasksOfList(listHash, "order_num");
				if(r.getCount()>0)
					r.moveToFirst();
				for(int i=0; i<r.getCount(); i++)
				{
					nert.add(new taskObject(r));
					r.moveToNext();
				}
				Collections.sort(nert, new MagicComparator());
				MagicTaskAdapter tx = new MagicTaskAdapter(context, R.layout.task_item, nert);
				lv.setAdapter(tx);
				tx.notifyDataSetChanged();
				return;
			case ID_HAND:
				return;
			case ID_TITLE:
				r = ListsActivity.syncHelper.db.getTasksOfList(listHash, "name");
				changeSort(r);
				break;
			case ID_DATE:
				r = ListsActivity.syncHelper.db.getTasksOfList(listHash, "logged, date");
				changeSort(r);
				break;
			case ID_PRIORITY:
				r = ListsActivity.syncHelper.db.getTasksOfList(listHash, "priority");
				changeSort(r);
			default:
				return;
			}
			TaskAdapter ta = new TaskAdapter(context, R.layout.task_item, r);
			lv.setAdapter(ta);
			ta.notifyDataSetChanged();
			
		}
	};
	
	public class MagicComparator implements Comparator<taskObject> {
		@Override
		public int compare(taskObject a, taskObject b) {
			int ratingA = a.dateWorth, ratingB = b.dateWorth;
			ratingA += a.priority*2;
			ratingB += b.priority*2;
			
			if(a.logged>0 && b.logged==0) return 1;
			else if(a.logged==0 && b.logged>0) return -1;
			else if(a.logged>0 && a.logged>0) return 0;
			return ratingB - ratingA;
		}
	}
	
	/*
	case "magic":
	list.sort(function(a, b) {

		var rating = {					//Get the days between now and the due date
			a: getDateWorth(a.date),	//If it's >14 days, the value is 1
			b: getDateWorth(b.date)		//Otherwise, it's 14 - diff + 1.
		}								// 13 = 2, 12 = 3, 11 = 4, etc
										// Highest possible is today: 0 = 13
		var worth = { none: 0, low: 2, medium: 4, high: 6 }
										// Highest possible: 19
		rating.a += worth[a.priority]
		rating.b += worth[b.priority]

		if(a.logged && !b.logged) return 1
		else if(!a.logged && b.logged) return -1
		else if(a.logged && b.logged) return 0

		return rating.b - rating.a

	})
	break
	 */
	
	class taskObject {
		String hash, name, notes, list, tags;
		int priority;
		long logged, date;
		
		int dateWorth;
		
		public taskObject(Cursor c)
		{
			hash = c.getString(c.getColumnIndex("hash"));
			name = c.getString(c.getColumnIndex("name"));
			notes = c.getString(c.getColumnIndex("notes"));
			list = c.getString(c.getColumnIndex("list"));
			tags = c.getString(c.getColumnIndex("tags"));
			priority = c.getInt(c.getColumnIndex("priority"));
			logged = c.getLong(c.getColumnIndex("logged"));
			date = c.getLong(c.getColumnIndex("date"));
			dateWorth = getDateWorth();
		}
		
		private int getDateWorth()
		{
			if(date == 0)
				return 0;
			Date due = new Date(date);
			Date today = new Date();
			
			Date one = new Date(due.getYear(), due.getMonth(), due.getDate());
			Date two = new Date(today.getYear(), today.getMonth(), today.getDate());
			
			int millisecondsPerDay = 1000 * 60 * 60 * 24;
			long millisBetween = one.getTime() - two.getTime();
			double days = (double)millisBetween / millisecondsPerDay;
			int diff = (int) Math.floor(days);
			if(diff > 14)
				diff = 14;
			return 14 - diff + 1;
			
		}
	}
	

	
	void changeSort(Cursor r)
	{
		ListsActivity.syncHelper.db.open();
		String tempHash;
		String tempHashString = "";
		if(r.getCount()>1)
			r.moveToFirst();
		for(int i=0; i<r.getCount(); i++)
		{
			tempHash = r.getString(r.getColumnIndex("hash"));
			if(i>0)
				tempHashString = tempHashString.concat("|");
			tempHashString = tempHashString.concat(tempHash);
			ListsActivity.syncHelper.db.modifyOrder(tempHash, i);
			r.moveToNext();
		}
		ListsActivity.syncHelper.db.modifyListOrder(listHash, tempHashString);
		System.out.println(tempHashString);
		ListsActivity.syncHelper.db.close();
	}
    
	
	static void expand(View view)
	{
		if(view!=null && view.findViewById(R.id.taskInfo).getVisibility()==View.GONE)
		{
		  view.findViewById(R.id.taskName).setVisibility(View.GONE);
		  view.findViewById(R.id.taskTime).setVisibility(View.GONE);
		  view.findViewById(R.id.taskName_edit).setVisibility(View.VISIBLE);
		  ((TextView)view.findViewById(R.id.taskName_edit)).setText(((TextView)view.findViewById(R.id.taskName)).getText());
		  
		  view.findViewById(R.id.taskInfo).setVisibility(View.VISIBLE);
		  lastClicked = view; //Skeptical Jon is skeptical
		}
	}
	
	static void collapse(View view)
	{
		if(view!=null && view.findViewById(R.id.taskInfo).getVisibility()!=View.GONE)
		{
		  view.findViewById(R.id.taskName).setVisibility(View.VISIBLE);
		  view.findViewById(R.id.taskTime).setVisibility(View.VISIBLE);
		  view.findViewById(R.id.taskName_edit).setVisibility(View.GONE);
		  ((TextView)view.findViewById(R.id.taskName)).setText(((TextView)view.findViewById(R.id.taskName_edit)).getText());
		  
		  view.findViewById(R.id.taskInfo).setVisibility(View.GONE);
		}
	}
	
	OnItemClickListener selectTask = new OnItemClickListener() 
    {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id)
      {
    	  System.out.println("LC: " + lastClicked);
    	  
    	  if(lastClicked==view &&
    			  view.findViewById(R.id.taskInfo).getVisibility()==View.GONE)
    	  {
    		  
    		  lastClicked.setBackgroundDrawable(normalDrawable);
    		  System.out.println("LCC: " + lastClicked);
    		  expand(view);
    	  }
    	  else
    	  {
    		  
    		  collapse(lastClicked);
    		  if(lastClicked!=null)
    			  lastClicked.setBackgroundDrawable(normalDrawable);
    		  lastClicked = view;
    		  lastClicked.setBackgroundDrawable(selectedDrawable);
    		  //lastClickedID = (String) ((TextView)lastClicked.findViewById(R.id.taskId)).getText();
    	  }
      }
    };
    
    boolean doBackThings()
    {
    	System.out.println(lastClicked);
    	if(editingTags!=null)
    	{
    		System.out.println("Finished Editing Tags");
    		editingTags.setVisibility(View.GONE);
    		((LinearLayout)editingTags.getParent()).getChildAt(1).setVisibility(View.VISIBLE);
    		editingTags = null;
    	}
    	else if(lastClicked!=null || lastClickedID!=null)
    	{
	    	collapse(lastClicked);
	    	if(lastClicked!=null)
	    		lastClicked.setBackgroundDrawable(normalDrawable);
	    	lastClicked = null;
	    	lastClickedID = null;
    	}
    	else
    		return true;
    		//context.finish();
    	return false;
    }
    
	
	
	
	static OnLongClickListener pressTag = new OnLongClickListener()
	{
		@Override
		public boolean onLongClick(View v)
		{
			LinearLayout tagparent = (LinearLayout) ((View)v.getParent());
			android.widget.HorizontalScrollView s = (android.widget.HorizontalScrollView) tagparent.getParent();
			LinearLayout sParent = (LinearLayout) s.getParent();
			EditText e = (EditText) sParent.findViewById(R.id.tags_edit);
			for(int i=0; i<(tagparent.getChildCount()); i=i+2)
			{
				if(i>0)
					e.append(", ");
				e.append(((TextView) (tagparent.getChildAt(i))).getText());
			}
			e.setVisibility(View.VISIBLE);
			int n = e.getText().toString().indexOf((String) ((TextView)v).getText());
			e.setSelection(n, n + ((TextView)v).getText().length());
			e.requestFocus();
			s.setVisibility(View.GONE);
			//editingTags = e;
			return true;
		}
	};
	
	
	
	String bit()
	{
		
		return Integer.toString((int)Math.floor(Math.random() *36), 36);
	}
	
	String part()
	{
		return bit() + bit() + bit() + bit();
	}
	
	String getID()
	{
		return part() + "-" + part() + "-" + part();
	}
	
}
