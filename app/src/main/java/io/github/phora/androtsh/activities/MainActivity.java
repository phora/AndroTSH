package io.github.phora.androtsh.activities;

import android.app.AlertDialog;
import android.app.ExpandableListActivity;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.CursorAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleCursorAdapter;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;

import io.github.phora.androtsh.DBHelper;
import io.github.phora.androtsh.network.NetworkUtils;
import io.github.phora.androtsh.R;
import io.github.phora.androtsh.network.RequestData;
import io.github.phora.androtsh.network.UploadData;
import io.github.phora.androtsh.adapters.UploadsCursorTreeAdapter;

public class MainActivity extends ExpandableListActivity {

    private Spinner server_spinner;

    static final int PICKING_FOR_UPLOAD = 1;
    private boolean actionModeEnabled;
    private DBHelper sqlhelper;
    private Context context;

    private class MsgOrInt {
        public String msg;
        public int progress;

        public MsgOrInt(String msg, int progress) {
            this.msg = msg;
            this.progress = progress;
        }
    }

    private class UploadFilesTask extends AsyncTask<Uri, MsgOrInt, List<UploadData>>
    {
        private final String serverUrl;
        private ProgressDialog pd;

        public UploadFilesTask(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        @Override
        protected List<UploadData> doInBackground(Uri... lists) {
            int count = lists.length;
            if (count < 1) return null;


            NetworkUtils nm = NetworkUtils.getInstance(getApplicationContext());
            HttpURLConnection connection = nm.openConnection(this.serverUrl);
            DataOutputStream dos;
            RequestData rd;

            pd.setMax(lists.length + 1);

            try {
                dos = new DataOutputStream(connection.getOutputStream());
                this.publishProgress(new MsgOrInt("Established connection", pd.getProgress()+1));
                rd = new RequestData(dos, getContentResolver());
                dos.writeBytes(RequestData.hyphens + RequestData.boundary + RequestData.crlf);
            }
            catch (IOException e) {
                this.publishProgress(new MsgOrInt("Unable to establish connection", 0));
                return null;
            }

            for (int i=0;i<lists.length;i++) {
                String filemsg = String.format("Putting %s/%s files on the server", i+1, lists.length);
                this.publishProgress(new MsgOrInt(filemsg, pd.getProgress()));
                try {
                    rd.addFile(lists[i]);
                    this.publishProgress(new MsgOrInt(null, pd.getProgress()+1));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Cannot upload "+lists[i], pd.getProgress()+1));
                    return null;
                }
            }
            try {
                this.publishProgress(new MsgOrInt("Getting response from server", pd.getProgress()));
                return nm.getUploadResults(this.serverUrl, connection);
            } catch (IOException e) {
                this.publishProgress(new MsgOrInt("Server did not respond", pd.getProgress()));
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(MsgOrInt... values) {
            if (values[0].msg != null) {
                pd.setMessage(values[0].msg);
            }
            pd.setProgress(values[0].progress);
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(context);
            pd.setTitle("Uploading files...");
            pd.setCancelable(false);
            pd.setIndeterminate(false);
            pd.show();
        }

        @Override
        protected void onPostExecute(List<UploadData> uploadDatas) {
            pd.dismiss();
            Log.d("MainActivity", "Got upload history "+(uploadDatas != null)+", updating GUI");
            if (uploadDatas != null) {
                addUploads(uploadDatas);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIsDark()) {
            setTheme(R.style.AppThemeDark);
        }
        else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        //populateActivity();

        server_spinner = (Spinner) findViewById(R.id.MainActivity_ServerSpinner);
        sqlhelper = DBHelper.getInstance(this);


        Intent intent = getIntent();
        final String action = intent.getAction();
        String type = intent.getType();

        String[] headerData = {"header", "complete_url", "dt"};
        String[] headerItemData = {"header", "complete_url"};
        int[] headerRsc = {R.id.UploadGroup_BigHeader, R.id.UploadGroup_CompleteUrl, R.id.UploadGroup_Date};
        int[] headerItemRsc = {R.id.UploadGroup_BigHeader, R.id.UploadGroup_CompleteUrl};
        ExpandableListAdapter adapter = new UploadsCursorTreeAdapter(this,
                R.layout.upload_group, R.layout.upload_group_item,
                headerData, headerRsc,
                headerItemData, headerItemRsc);
        setListAdapter(adapter);

        getExpandableListView().setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView expandableListView, View view, int pos, long id) {
                if (actionModeEnabled) {
                    if (!expandableListView.isGroupExpanded(pos))
                        expandableListView.setItemChecked(pos, !expandableListView.isItemChecked(pos));
                    return true;
                }
                return false;
            }
        });

        getExpandableListView().setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int groupPos, int childPos, long id) {
                if (actionModeEnabled) {
                    long packedPos = expandableListView.getPackedPositionForChild(groupPos, childPos);
                    int flatPos = expandableListView.getFlatListPosition(packedPos);
                    expandableListView.setItemChecked(flatPos, !expandableListView.isItemChecked(flatPos));
                    return true;
                }
                return false;
            }
        });

        getExpandableListView().setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                int item_count = getExpandableListView().getCheckedItemCount();
                if (item_count == 1) {
                    actionMode.setSubtitle(getString(R.string.CABMenu_SingleItem));
                } else {
                    actionMode.setSubtitle(String.format(getString(R.string.CABMenu_MultipleItems), item_count));
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                actionModeEnabled = true;
                MenuInflater mi = getMenuInflater();
                actionMode.setTitle(getString(R.string.CABMenu_SelectItems));
                actionMode.setSubtitle(getString(R.string.CABMenu_SingleItem));
                mi.inflate(R.menu.uploads_cab_menu, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                //getExpandableListView().get
                String s;
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData clipdata;
                Intent intent;

                switch (menuItem.getItemId()) {
                    case R.id.copy_separate:
                        Log.d("ItemCAB", "Copied things as separate links");
                        s = getBatchSeparate();
                        clipdata = ClipData.newPlainText(getResources().getString(R.string.Copy_Label), s);
                        clipboard.setPrimaryClip(clipdata);
                        Toast.makeText(context, getString(R.string.Toast_Copy), Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.copy_zipped:
                        Log.d("ItemCAB", "Copied things as combined zip");
                        s = getBatchZip();
                        clipdata = ClipData.newPlainText(getResources().getString(R.string.Copy_Label), s);
                        clipboard.setPrimaryClip(clipdata);
                        Toast.makeText(context, getString(R.string.Toast_Copy_Zip), Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.share_separate:
                        Log.d("ItemCAB", "Sharing as separate");
                        s = getBatchSeparate();
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, s);
                        startActivity(Intent.createChooser(intent, getString(R.string.Share_Title)));
                        break;
                    case R.id.share_zipped:
                        Log.d("ItemCAB", "Sharing as combined zip");
                        s = getBatchZip();
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, s);
                        startActivity(Intent.createChooser(intent, getString(R.string.Share_Title_Zip)));
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                actionModeEnabled = false;
            }
        });

        String[] serverData = {"base_url", "expiry"};
        int[] serverRsc = {R.id.ServerItem_Url, R.id.ServerItem_Expiry};
        SimpleCursorAdapter servAdap = new SimpleCursorAdapter(this, R.layout.server_item,
                sqlhelper.getAllServers(), serverData, serverRsc, CursorAdapter.FLAG_AUTO_REQUERY);
        server_spinner.setAdapter(servAdap);

        /* if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
            Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
            String server_path = selected_server.getString(selected_server.getColumnIndex(AndroTSHSQLiteHelper.BASE_URL));
            ClipData clippy = intent.getClipData();
            Log.d("MainActivity", "Received " + clippy.getItemCount() + " files from another app");
            LinkedList<Uri> all_the_files = new LinkedList<Uri>();
            for (int i=0;i<clippy.getItemCount();i++) {
                all_the_files.add(clippy.getItemAt(i).getUri());
            }
            new UploadFilesTask(server_path).execute(all_the_files);
        }
        else {
            Log.d("MainActivity", "Doing GUI interaction");
            //other stuff
        } */
    }

    public String getBatchZip() {
        StringBuilder sb = new StringBuilder();
        SparseBooleanArray selection = getExpandableListView().getCheckedItemPositions();
        LinkedList<Long> ids = new LinkedList<Long>();

        for (int i=0;i<getExpandableListAdapter().getGroupCount();i++) {
            if (selection.get(i, false)) {
                Cursor c = (Cursor)getExpandableListView().getItemAtPosition(i);
                if (c.getString(c.getColumnIndex("header")).equals("(multiple)")) {
                    long pid = c.getLong(c.getColumnIndex(DBHelper.COLUMN_ID));
                    int childrenCount = getExpandableListAdapter().getChildrenCount(i);
                    for (int j=0;j<childrenCount;j++) {
                        Cursor chitem = (Cursor)getExpandableListAdapter().getChild(i, j);
                        ids.add(chitem.getLong(chitem.getColumnIndex(DBHelper.COLUMN_ID)));
                    }
                }
                else {
                    ids.add(c.getLong(c.getColumnIndex(DBHelper.COLUMN_ID)));
                }
            }
        }
        Cursor groupDownloads = sqlhelper.batchAsArchive(ids.toArray(new Long[ids.size()]), true);
        for (int i=0;i<groupDownloads.getCount();i++) {
            groupDownloads.moveToPosition(i);
            sb.append(groupDownloads.getString(groupDownloads.getColumnIndex("tokpath")));
            sb.append("\n");
        }
        groupDownloads.close();

        return sb.toString();
    }

    public String getBatchSeparate() {
        StringBuilder sb = new StringBuilder();
        SparseBooleanArray selection = getExpandableListView().getCheckedItemPositions();

        for (int i=0;i<getExpandableListAdapter().getGroupCount();i++) {
            if (selection.get(i, false)) {
                Cursor c = (Cursor)getExpandableListView().getItemAtPosition(i);
                if (c.getString(c.getColumnIndex("header")).equals("(multiple)")) {
                    long pid = c.getLong(c.getColumnIndex(DBHelper.COLUMN_ID));
                    Cursor groupDownload = sqlhelper.batchAsArchive(pid, true);
                    groupDownload.moveToFirst();

                    sb.append(c.getString(c.getColumnIndex(DBHelper.BASE_URL)));
                    sb.append("/");
                    sb.append(groupDownload.getString(groupDownload.getColumnIndex("tokpath")));
                    sb.append("\n");
                    groupDownload.close();
                }
                else {
                    sb.append(c.getString(c.getColumnIndex("complete_url")));
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private boolean getIsDark() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isdark = preferences.getBoolean("isDark", false);
        return isdark;
    }

    public void requestFiles(View view) {
        Intent intent;
        Intent requestFilesIntent = new Intent(Intent.ACTION_GET_CONTENT);
        requestFilesIntent.setType("*/*");
        requestFilesIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        requestFilesIntent.addCategory(Intent.CATEGORY_OPENABLE);

        intent = Intent.createChooser(requestFilesIntent, getString(R.string.MainActivity_ChooseFiles));
        startActivityForResult(intent, PICKING_FOR_UPLOAD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICKING_FOR_UPLOAD) {
            if (resultCode == RESULT_OK) {
                Uri singleData = data.getData();
                ClipData clippy = data.getClipData();

                Cursor selectedServer = (Cursor)server_spinner.getSelectedItem();
                String serverPath = selectedServer.getString(selectedServer.getColumnIndex(DBHelper.BASE_URL));

                if (clippy != null) {
                    //clipdata holds uris
                    Log.d("MainActivity", String.format(getString(R.string.MainActivity_PickedMultiple), clippy.getItemCount()));
                    Uri[] allTheFiles = new Uri[clippy.getItemCount()];
                    for (int i=0;i<clippy.getItemCount();i++) {
                        allTheFiles[i] = clippy.getItemAt(i).getUri();
                    }
                    new UploadFilesTask(serverPath).execute(allTheFiles);
                }
                else if (singleData != null) {
                    Log.d("MainActivity", getString(R.string.MainActivity_PickedSingle));
                    //Log.d("MainActivity", FileUtils.getPath(getApplicationContext(), singleData));
                    new UploadFilesTask(serverPath).execute(singleData);
                }
                //do something
            }
        }
    }

    private void addUploads(List<UploadData> uploads) {
        long parentId = -1;

        for (UploadData ud: uploads) {
            if (ud.getFPath() == null) {
                parentId = sqlhelper.addUpload(ud.getServerUrl(), ud.getToken(), null);
            }
            else if (parentId != -1) {
                sqlhelper.addUpload(ud.getServerUrl(), ud.getToken(), ud.getFPath(), parentId, false);
            }
            else {
                sqlhelper.addUpload(ud.getServerUrl(), ud.getToken(), ud.getFPath());
            }
        }

        UploadsCursorTreeAdapter adap = (UploadsCursorTreeAdapter)getExpandableListAdapter();
        adap.changeCursor(sqlhelper.getAllGroups()); //only do this if we really need to
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        else if (id == R.id.action_about) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            View view = LayoutInflater.from(this).inflate(R.layout.about_dialog, null);
            String verName;
            try {
                verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            }
            catch (PackageManager.NameNotFoundException e) {
                verName = null;
            }
            TextView textView = (TextView)view.findViewById(R.id.AboutDialog_Version);
            textView.setText(getString(R.string.About_Version, verName));

            builder.setTitle(getString(R.string.About_App, getString(R.string.AppName)));
            builder.setView(view);
            builder.setNegativeButton(R.string.OK, null);
            builder.create().show();
            return true;
        }
        else if (id == R.id.action_prune) {
            sqlhelper.trimHistory();
            UploadsCursorTreeAdapter adap = (UploadsCursorTreeAdapter)getExpandableListAdapter();
            adap.changeCursor(sqlhelper.getAllGroups());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
