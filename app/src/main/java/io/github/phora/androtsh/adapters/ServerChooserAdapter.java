package io.github.phora.androtsh.adapters;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import io.github.phora.androtsh.DBHelper;
import io.github.phora.androtsh.CheckableLinearLayout;
import io.github.phora.androtsh.R;

/**
 * Created by phora on 8/21/15.
 */
public class ServerChooserAdapter extends ResourceCursorAdapter {
    //column field caches
    private static int URL_IDX = -1;
    private static int EXPIR_IDX = -1;
    private static int DEF_IDX = -1;

    private long curId = -1;


    public ServerChooserAdapter(Context context, Cursor c, boolean autoRequery) {
        super(context, R.layout.server_item_choose, c, autoRequery);
    }

    public ServerChooserAdapter(Context context, Cursor c, int flags) {
        super(context, R.layout.server_item_choose, c, flags);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        CheckableLinearLayout checkMe = (CheckableLinearLayout)view;
        CheckedTextView baseUrlView = (CheckedTextView) view.findViewById(R.id.ServerItem_Url);
        TextView expiryView = (TextView) view.findViewById(R.id.ServerItem_Expiry);

        if (URL_IDX == -1) {
            URL_IDX = cursor.getColumnIndex(DBHelper.BASE_URL);
        }
        if (EXPIR_IDX == -1) {
            EXPIR_IDX = cursor.getColumnIndex("expiry");
        }
        if (DEF_IDX == -1) {
            DEF_IDX = cursor.getColumnIndex(DBHelper.SERVER_DEFAULT);
        }

        boolean isChecked = (cursor.getInt(DEF_IDX) == 1);
        String base_url = cursor.getString(URL_IDX);
        String expiry = cursor.getString(EXPIR_IDX);

        Log.d("ServerChooserAdapter", "def flag: "+cursor.getInt(DEF_IDX));

        checkMe.setChecked(isChecked);
        baseUrlView.setText(base_url);
        expiryView.setText(expiry);

        if (isChecked) {
            Log.d("ServerChooserAdapter", base_url + " is the default");
            curId = cursor.getLong(cursor.getColumnIndex(DBHelper.COLUMN_ID));
        }
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        View v = super.getView(pos, convertView, parent);
        ListView lv = (ListView)parent;
        CheckableLinearLayout cll = (CheckableLinearLayout)v;

        //move this outside of the adapter, somehow...
        if (cll.isChecked()) {
            lv.setSelection(pos);
            lv.setItemChecked(pos, true);
        }

        return v;
    }

    public long getCurId() {
        return curId;
    }

    public void setCurId(long curId) {
        this.curId = curId;
    }
}
