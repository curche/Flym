/**
 * FeedEx
 * 
 * Copyright (c) 2012-2013 Frederic Julian
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fred.feedex.adapter;

import java.util.Date;
import java.util.Vector;

import net.fred.feedex.Constants;
import net.fred.feedex.MainApplication;
import net.fred.feedex.R;
import net.fred.feedex.provider.FeedData;
import net.fred.feedex.provider.FeedData.EntryColumns;
import net.fred.feedex.provider.FeedData.FeedColumns;
import net.fred.feedex.provider.FeedDataContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class EntriesCursorAdapter extends ResourceCursorAdapter {
	private int titleColumnPosition;

	private int dateColumn;
	private int isReadColumn;
	private int favoriteColumn;
	private int idColumn;
	private int feedIconColumn;
	private int feedNameColumn;
	private int linkColumn;

	private final Uri uri;
	private final boolean showFeedInfo;

	private final Vector<Long> markedAsRead = new Vector<Long>();
	private final Vector<Long> markedAsUnread = new Vector<Long>();
	private final Vector<Long> favorited = new Vector<Long>();
	private final Vector<Long> unfavorited = new Vector<Long>();

	public EntriesCursorAdapter(Context context, Uri uri, Cursor cursor, boolean showFeedInfo) {
		super(context, R.layout.entry_list_item, cursor, 0);
		this.uri = uri;
		this.showFeedInfo = showFeedInfo;

		reinit(cursor);
	}

	@Override
	public void bindView(View view, final Context context, Cursor cursor) {
		final TextView textView = (TextView) view.findViewById(android.R.id.text1);
		textView.setText(cursor.getString(titleColumnPosition));

		final TextView dateTextView = (TextView) view.findViewById(android.R.id.text2);
		final ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
		final long id = cursor.getLong(idColumn);
		view.setTag(cursor.getString(linkColumn));
		final boolean favorite = !unfavorited.contains(id) && (cursor.getInt(favoriteColumn) == 1 || favorited.contains(id));
		final CheckBox viewCheckBox = (CheckBox) view.findViewById(android.R.id.checkbox);

		imageView.setImageResource(favorite ? R.drawable.rating_important : R.drawable.rating_not_important);
		imageView.setTag(favorite ? Constants.TRUE : Constants.FALSE);
		imageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				boolean newFavorite = !Constants.TRUE.equals(view.getTag());

				if (newFavorite) {
					view.setTag(Constants.TRUE);
					imageView.setImageResource(R.drawable.rating_important);
					favorited.add(id);
					unfavorited.remove(id);
				} else {
					view.setTag(Constants.FALSE);
					imageView.setImageResource(R.drawable.rating_not_important);
					unfavorited.add(id);
					favorited.remove(id);
				}

				ContentValues values = new ContentValues();

				values.put(EntryColumns.IS_FAVORITE, newFavorite ? 1 : 0);

				ContentResolver cr = MainApplication.getAppContext().getContentResolver();
				if (cr.update(uri, values, new StringBuilder(EntryColumns._ID).append(Constants.DB_ARG).toString(),
						new String[] { Long.toString(id) }) > 0) {
					if (!EntryColumns.FAVORITES_CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.FAVORITES_CONTENT_URI, id), null);
					}

					if (!EntryColumns.CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.CONTENT_URI, id), null);
					}
				}
			}
		});

		Date date = new Date(cursor.getLong(dateColumn));

		if (showFeedInfo && feedIconColumn > -1 && feedNameColumn > -1) {
			byte[] iconBytes = cursor.getBlob(feedIconColumn);
			dateTextView.setText(new StringBuilder(Constants.DATE_FORMAT.format(date)).append(' ').append(Constants.TIME_FORMAT.format(date))
					.append(Constants.COMMA_SPACE).append(cursor.getString(feedNameColumn)));

			if (iconBytes != null && iconBytes.length > 0) {
				Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);

				if (bitmap != null) {
					int bitmapSizeInDip = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, context.getResources()
							.getDisplayMetrics());
					if (bitmap.getHeight() != bitmapSizeInDip) {
						bitmap = Bitmap.createScaledBitmap(bitmap, bitmapSizeInDip, bitmapSizeInDip, false);
					}
				}
				dateTextView.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(context.getResources(), bitmap), null, null, null);
			} else {
				dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
			}

		} else {
			textView.setText(cursor.getString(titleColumnPosition));
			dateTextView.setText(new StringBuilder(Constants.DATE_FORMAT.format(date)).append(' ').append(Constants.TIME_FORMAT.format(date)));
		}

		viewCheckBox.setOnCheckedChangeListener(null);
		if (markedAsUnread.contains(id) || (cursor.isNull(isReadColumn) && !markedAsRead.contains(id))) {
			textView.setEnabled(true);
			dateTextView.setEnabled(true);
			viewCheckBox.setChecked(false);
		} else {
			textView.setEnabled(false);
			dateTextView.setEnabled(false);
			viewCheckBox.setChecked(true);
		}

		viewCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					markAsRead(id);
					textView.setEnabled(false);
					dateTextView.setEnabled(false);
				} else {
					markAsUnread(id);
					textView.setEnabled(true);
					dateTextView.setEnabled(true);
				}
			}
		});
	}

	public void markAllAsRead() {
		markedAsRead.clear();
		markedAsUnread.clear();

		new Thread() {
			@Override
			public void run() {
				ContentResolver cr = MainApplication.getAppContext().getContentResolver();

				if (cr.update(uri, FeedData.getReadContentValues(), EntryColumns.WHERE_UNREAD, null) > 0) {
					cr.notifyChange(FeedColumns.CONTENT_URI, null);
					cr.notifyChange(FeedColumns.GROUPS_CONTENT_URI, null);
					cr.notifyChange(EntryColumns.FAVORITES_CONTENT_URI, null);
				}
			}
		}.start();
	}

	private void markAsRead(final long id) {
		markedAsRead.add(id);
		markedAsUnread.remove(id);

		new Thread() {
			@Override
			public void run() {
				ContentResolver cr = MainApplication.getAppContext().getContentResolver();
				if (cr.update(ContentUris.withAppendedId(uri, id), FeedData.getReadContentValues(), null, null) > 0) {
					String feedId = FeedDataContentProvider.getFeedIdFromEntryId(id);
					if (feedId != null) {
						cr.notifyChange(FeedColumns.CONTENT_URI, null);
						FeedDataContentProvider.notifyGroupFromFeedId(feedId);
					}

					if (!EntryColumns.FAVORITES_CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.FAVORITES_CONTENT_URI, id), null);
					}

					if (!EntryColumns.CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.CONTENT_URI, id), null);
					}
				}
			}
		}.start();
	}

	private void markAsUnread(final long id) {
		markedAsUnread.add(id);
		markedAsRead.remove(id);

		new Thread() {
			@Override
			public void run() {
				ContentResolver cr = MainApplication.getAppContext().getContentResolver();
				if (cr.update(ContentUris.withAppendedId(uri, id), FeedData.getUnreadContentValues(), null, null) > 0) {
					String feedId = FeedDataContentProvider.getFeedIdFromEntryId(id);
					if (feedId != null) {
						cr.notifyChange(FeedColumns.CONTENT_URI, null);
						FeedDataContentProvider.notifyGroupFromFeedId(feedId);
					}

					if (!EntryColumns.FAVORITES_CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.FAVORITES_CONTENT_URI, id), null);
					}

					if (!EntryColumns.CONTENT_URI.equals(uri)) {
						cr.notifyChange(ContentUris.withAppendedId(EntryColumns.CONTENT_URI, id), null);
					}
				}
			}
		}.start();
	}

	@Override
	public void changeCursor(Cursor cursor) {
		reinit(cursor);
		super.changeCursor(cursor);
	}

	@Override
	public Cursor swapCursor(Cursor newCursor) {
		reinit(newCursor);
		return super.swapCursor(newCursor);
	}

	@Override
	public void notifyDataSetChanged() {
		reinit(null);
		super.notifyDataSetChanged();
	}

	@Override
	public void notifyDataSetInvalidated() {
		reinit(null);
		super.notifyDataSetInvalidated();
	}

	private void reinit(Cursor cursor) {
		markedAsRead.clear();
		markedAsUnread.clear();
		favorited.clear();
		unfavorited.clear();

		if (cursor != null) {
			titleColumnPosition = cursor.getColumnIndex(EntryColumns.TITLE);
			dateColumn = cursor.getColumnIndex(EntryColumns.DATE);
			isReadColumn = cursor.getColumnIndex(EntryColumns.IS_READ);
			favoriteColumn = cursor.getColumnIndex(EntryColumns.IS_FAVORITE);
			idColumn = cursor.getColumnIndex(EntryColumns._ID);
			linkColumn = cursor.getColumnIndex(EntryColumns.LINK);
			if (showFeedInfo) {
				feedIconColumn = cursor.getColumnIndex(FeedColumns.ICON);
				feedNameColumn = cursor.getColumnIndex(FeedColumns.NAME);
			}
		}
	}
}
