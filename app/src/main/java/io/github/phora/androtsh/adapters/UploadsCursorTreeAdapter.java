package io.github.phora.androtsh.adapters;

import android.content.Context;
import android.database.Cursor;
import android.widget.SimpleCursorTreeAdapter;

import io.github.phora.androtsh.DBHelper;

/**
 * Created by phora on 8/19/15.
 */
public class UploadsCursorTreeAdapter extends SimpleCursorTreeAdapter {

    private Context mContext;

    public UploadsCursorTreeAdapter(Context context,
                                    int groupLayout, int childLayout, String[] groupFrom,
                                    int[] groupTo, String[] childrenFrom, int[] childrenTo) {

        super(context, DBHelper.getInstance(context).getAllGroups(), groupLayout, groupFrom, groupTo, childLayout, childrenFrom, childrenTo);
        mContext = context;
    }

    @Override
    protected Cursor getChildrenCursor(Cursor cursor) {

        DBHelper sqlhelper = DBHelper.getInstance(mContext);
        long col_id = cursor.getLong(cursor.getColumnIndex(sqlhelper.COLUMN_ID));

        Cursor children = sqlhelper.getUploadsInGroup(col_id);
        return children;
    }
}
