/*
 * Copyright (C) 2015 The Nevolution Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.nevo;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RestrictTo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * Backward support for {@link android.service.notification.StatusBarNotification StatusBarNotification}
 *
 * Created by Oasis on 2014/11/28.
 */
public class StatusBarNotificationCompat extends StatusBarNotification {

	private final String key;
	private final String groupKey;

	@RestrictTo(LIBRARY_GROUP)
	public StatusBarNotificationCompat(final String pkg, final String opPkg, final int id, final String tag, final int uid, final int initialPid,
									   final int score, final Notification notification, final UserHandle user, final long postTime) {
		super(pkg, opPkg, id, tag, uid, initialPid, score, notification, user, postTime);
		key = SbnCompat.keyOf(this); groupKey = SbnCompat.groupKeyOf(this);
	}

	@Override public String getKey() { return key; }
	@Override public String getGroupKey() { return groupKey; }

	private String getBaseKey() { return super.getKey(); }
	private String getBaseGroupKey() { return super.getGroupKey(); }

	/** {@link StatusBarNotification#getUser()} is hidden but accessible in API level 18~20 */
	@RestrictTo(LIBRARY_GROUP) @Override public UserHandle getUser() {
        try { return super.getUser(); }
        catch (final Throwable t) { return Process.myUserHandle(); }
    }

	public StatusBarNotificationCompat(final Parcel parcel) {
    	super(parcel);
    	key = SbnCompat.keyOf(this);
    	groupKey = SbnCompat.groupKeyOf(this);
    }

	public static final Parcelable.Creator<StatusBarNotificationCompat> CREATOR = new Parcelable.Creator<StatusBarNotificationCompat>() {
		public StatusBarNotificationCompat createFromParcel(final Parcel parcel) { return new StatusBarNotificationCompat(parcel); }
		public StatusBarNotificationCompat[] newArray(final int size) { return new StatusBarNotificationCompat[size]; }
	};

	private static final String TAG = "SbnCompat";

	/** Shortcut for cleaner code */
	@RestrictTo(LIBRARY_GROUP) public static class SbnCompat {

		public static String keyOf(final StatusBarNotification sbn) {
			if (! (sbn instanceof StatusBarNotificationCompat)) return sbn.getKey();
			final StatusBarNotificationCompat sbnc = (StatusBarNotificationCompat) sbn;
			final String key = sbnc.getKey();
			if (key != null) return key;				// May actually be null when called by the constructor
			return ((StatusBarNotificationCompat) sbn).getBaseKey();
		}

		static String buildKey(final StatusBarNotification sbn) {
			return buildKey(userOf(sbn), sbn.getPackageName(), sbn.getId(), sbn.getTag(), getUid(sbn));
		}

		static String buildKey(final UserHandle user, final String pkg, final int id, final String tag, final int uid) {
			return String.valueOf(user.hashCode()) + '|' + pkg + '|' + id + '|' + tag + '|' + uid;
		}

		public static String groupKeyOf(final StatusBarNotification sbn) {
			if (! (sbn instanceof StatusBarNotificationCompat)) return sbn.getGroupKey();
			final String group_key = ((StatusBarNotificationCompat) sbn).groupKey;
			if (group_key != null) return group_key;	// May actually be null when called by the constructor
			return ((StatusBarNotificationCompat) sbn).getBaseGroupKey();
		}

		/** {@link StatusBarNotification#getUser()} is hidden but accessible in API level 18~20 */
		@SuppressLint("NewApi") static UserHandle userOf(final StatusBarNotification sbn) {
			return sbn.getUser();
		}

		static int getUid(final StatusBarNotification sbn) {
			if (sMethodGetUid != null)
				try { return (int) sMethodGetUid.invoke(sbn); } catch (final Exception ignored) {}
			if (sFieldUid != null)
				try { return (int) sFieldUid.get(sbn); } catch (final IllegalAccessException ignored) {}
			// TODO: PackageManager.getPackageUid()
			Log.e(TAG, "Incompatible ROM: StatusBarNotification");
			return 0;
		}

		private static final Method sMethodGetUid;
		private static final Field sFieldUid;
		static {
			Method method = null; Field field = null;
			try {
				method = StatusBarNotification.class.getMethod("getUid");
			} catch (final NoSuchMethodException ignored) {}
			sMethodGetUid = method;
			if (method == null) try {       // If no such method, try accessing the field
				field = StatusBarNotification.class.getDeclaredField("uid");
				field.setAccessible(true);
			} catch (final NoSuchFieldException ignored) {}
			sFieldUid = field;
		}
	}
}
