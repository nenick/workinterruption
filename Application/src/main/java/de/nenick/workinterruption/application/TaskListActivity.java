package de.nenick.workinterruption.application;

import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import de.nenick.workinterruption.R;
import de.nenick.workinterruption.application.functions.DeleteTaskFunction;
import de.nenick.workinterruption.application.functions.GetTaskListFunction;

public class TaskListActivity extends android.app.ListActivity  {

    private static final int DELETE_ID = Menu.FIRST + 1;

    private GetTaskListFunction getTaskListFunction = new GetTaskListFunction();
    private DeleteTaskFunction deleteTaskFunction = new DeleteTaskFunction();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.task_list);
        setupActionBar();

        this.getListView().setDividerHeight(2);
        getTaskListFunction.apply(this, getListView());
        registerForContextMenu(getListView());
    }

    private void setupActionBar() {
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                deleteTaskFunction.apply(item, getContentResolver());
                getTaskListFunction.apply(this, getListView());
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete);
    }
}

