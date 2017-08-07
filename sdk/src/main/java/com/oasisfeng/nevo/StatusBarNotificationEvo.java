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

import android.app.Notification;
import android.os.Binder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * Ease the code across Nevolution with following features:
 * <ul>
 *   <li>Automatic proxy for heavy notification instance as lazy binder.</li>
 *   <li>Methods for decorator to alter package, tag, ID, key and group key.</li>
 * </ul>
 *
 * Two states: wrapper and proxy (via binder)
 *
 * Created by Oasis on 2015/1/18.
 */
public class StatusBarNotificationEvo extends StatusBarNotificationCompat {

	public static StatusBarNotificationEvo from(final StatusBarNotification sbn) {
		if (sbn instanceof StatusBarNotificationEvo) return (StatusBarNotificationEvo) sbn;
		return new StatusBarNotificationEvo(sbn.getPackageName(), null/* opPkg */, sbn.getId(), sbn.getTag(), SbnCompat.getUid(sbn),
				0/* initialPid */, 0/* score */, sbn.getNotification(), SbnCompat.userOf(sbn), sbn.getPostTime());
	}

	@RestrictTo(LIBRARY_GROUP)
	public static StatusBarNotificationEvo from(final StatusBarNotification sbn, final NotificationHolder.OnDemandSuppliers suppliers) {
		if (sbn instanceof StatusBarNotificationEvo) {
			final StatusBarNotificationEvo sbne = (StatusBarNotificationEvo) sbn;
			final StatusBarNotificationEvo clone = new StatusBarNotificationEvo(sbn.getPackageName(), null/* opPkg */, sbne.getOriginalId(),
					sbne.getOriginalTag(), SbnCompat.getUid(sbn), 0, 0, sbn.getNotification(), SbnCompat.userOf(sbn), sbn.getPostTime(), suppliers);
			clone.tag = sbne.tag; clone.id = sbne.id; clone.tag_decorated = sbne.tag_decorated;
			clone.updateKey();
			return clone;
		}
		return new StatusBarNotificationEvo(sbn.getPackageName(), null, sbn.getId(), sbn.getTag(),
				SbnCompat.getUid(sbn), 0, 0, sbn.getNotification(), SbnCompat.userOf(sbn), sbn.getPostTime(), suppliers);
	}

	/** Clone the data fields only (suppliers, notification cache will not be cloned and holder */
	@Override public StatusBarNotificationEvo clone() {
		final StatusBarNotificationEvo clone = from(super.clone());
		clone.tag = tag; clone.id = id; clone.tag_decorated = tag_decorated;
		clone.updateKey();
		return clone;
	}

	public StatusBarNotificationEvo(final String pkg, final String opPkg, final int id, final String tag,
									final int uid, final int initialPid, final int score,
									final Notification notification, final UserHandle user, final long postTime) {
		super(pkg, opPkg, id, tag, uid, initialPid, score, notification, user, postTime);
		holder = new NotificationHolder(notification);
	}

	private StatusBarNotificationEvo(final String pkg, final String opPkg, final int id, final String tag,
									final int uid, final int initialPid, final int score,
									final Notification notification, final UserHandle user, final long postTime,
									final NotificationHolder.OnDemandSuppliers suppliers) {
		super(pkg, opPkg, id, tag, uid, initialPid, score, notification, user, postTime);
		holder = new NotificationHolder(notification, suppliers);
	}

	public StatusBarNotificationEvo setTag(final @Nullable String tag) {
		if (equal(tag, this.tag)) return this;
		if (equal(tag, super.getTag())) {			// Equal to the original tag
			this.tag = null; tag_decorated = false;		// Clear the tag decoration
		} else {
			this.tag = tag; tag_decorated = true;
		}
		updateKey();
		return this;
	}

	public StatusBarNotificationEvo setId(final int id) {
		if (this.id != null && id == this.id) return this;
		if (id == super.getId()) this.id = null;	// Equal to the original ID, clear the decorated value
		else this.id = id;
		updateKey();
		return this;
	}

	private void updateKey() {
		if (! tag_decorated && id == null) key = null;
		else key = SbnCompat.buildKey(this);
	}

	@Override public String getTag() { return tag_decorated ? tag : super.getTag(); }
	@Override public int getId() { return id != null ? id : super.getId(); }
	@Override public String getKey() { return key != null ? key : super.getKey(); }
	public String getOriginalKey() { return super.getKey(); }
	private String getOriginalTag() { return super.getTag(); }
	private int getOriginalId() { return super.getId(); }

	/**
	 * Beware, calling this method on remote instance will retrieve the whole instance, which is inefficient and slow.
	 * This local instance will also be marked as "dirty", greatly increasing the cost of future {@link #writeToParcel(Parcel, int)},
	 * even if nothing is actually changed.
	 *
	 * @deprecated Consider using {@link #notification()} whenever possible to avoid the overhead of this method.
	 */
	@Deprecated @Override public Notification getNotification() {
		try {
			if (holder == null) return super.getNotification();	// holder is null only if called by super constructor StatusBarNotification().
			if (holder instanceof INotification.Stub) return holder.get();	// Direct fetch for local instance
			if (notification == null) {
				try {
					final long begin = SystemClock.uptimeMillis();
					notification = holder.get();
					final long elapse = SystemClock.uptimeMillis() - begin;
					if (elapse > 1) Log.w(TAG, "Retrieving the whole instance of remote notification spent " + elapse + "ms");
				} catch (final RuntimeException e) {
					Log.e(TAG, "Failed to retrieve notification: " + getKey());
					throw e;
				}
				NotificationCompat.getExtras(notification).setClassLoader(StatusBarNotificationEvo.class.getClassLoader());	// For our parcelable classes
			}
		} catch (final RemoteException e) { throw new IllegalStateException(e); }
		return notification;
	}

	/** Get the interface of notification for modification, to avoid the overhead of get the whole notification */
	public INotification notification() {
		return holder;
	}

	@Override public boolean isOngoing() {
		try {
			return (notification().getFlags() & Notification.FLAG_ONGOING_EVENT) != 0;
		} catch (final RemoteException e) { throw new IllegalStateException(e); }
	}

	@Override public boolean isClearable() {
		try {
			final int flags = notification().getFlags();
			return (flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR)) == 0;
		} catch (final RemoteException e) { throw new IllegalStateException(e); }
	}

	/** Write all fields except the Notification which is passed as IPC holder */
	@Override public void writeToParcel(final Parcel out, final int flags) {
		out.writeInt(PARCEL_MAGIC);
		out.writeString(getPackageName());
		out.writeInt(super.getId());
		if (super.getTag() != null) {
			out.writeInt(1);
			out.writeString(super.getTag());
		} else out.writeInt(0);
		out.writeInt(SbnCompat.getUid(this));
		getUser().writeToParcel(out, flags);
		out.writeLong(getPostTime());
		out.writeStrongInterface(notification == null ? holder : new NotificationHolder(notification));	// The local copy of notification is "dirty" (possibly modified), hence needs to be updated.
		if (id != null) {
			out.writeInt(1);
			out.writeInt(id);
		} else out.writeInt(0);
		if (tag_decorated) {
			out.writeInt(1);
			out.writeString(tag);
		} else out.writeInt(0);
	}

    // Parcel written by plain StatusBarNotification
	private StatusBarNotificationEvo(final Parcel in, final @Nullable INotification holder) {
		super(in);
		//noinspection deprecation
		this.holder = holder != null ? holder : new NotificationHolder(getNotification());
	}

	private StatusBarNotificationEvo(final Parcel in) {
		super(in.readString(), null, in.readInt(), in.readInt() != 0 ? in.readString() : null, in.readInt(), 0, 0,
				NULL_NOTIFICATION, UserHandle.readFromParcel(in), in.readLong());
		holder = INotification.Stub.asInterface(in.readStrongBinder());
		if (in.readInt() == 0) id = null;
		else id = in.readInt();
		tag_decorated = in.readInt() != 0;
		if (tag_decorated) tag = in.readString();
		updateKey();
	}

	public static final Parcelable.Creator<StatusBarNotificationEvo> CREATOR = new Parcelable.Creator<StatusBarNotificationEvo>() {

		@Override public StatusBarNotificationEvo createFromParcel(final Parcel source) {
			final int pos = source.dataPosition();
			final int magic = source.readInt();
			//noinspection deprecation
			if (magic == PARCEL_MAGIC) return new StatusBarNotificationEvo(source);
			// Then it should be an instance of StatusBarNotification, rewind and un-parcel in that way.
			source.setDataPosition(pos);
			return new StatusBarNotificationEvo(source, null);
		}

		public StatusBarNotificationEvo[] newArray(final int size) { return new StatusBarNotificationEvo[size]; }
	};

	private static boolean equal(final @Nullable Object a, final @Nullable Object b) {
		return a == b || (a != null && a.equals(b));
	}

	@Override public String toString() {
		final StringBuilder string = new StringBuilder("StatusBarNotificationEvo(key=");
		string.append(getOriginalKey());
		if (key != null) string.append(" -> ").append(key);
		string.append(": ");
		if (holder instanceof Binder) try { string.append(holder.get()); } catch (final RemoteException ignored) {}	// Should never happen
		else string.append("remote");
		string.append(')');
		return string.toString();
	}

	private String tag;     // Null is allowed, that's why "tag_decorated" is introduced.
    private @Nullable Integer id;
    private boolean tag_decorated;
    private final INotification holder;
	private transient String key;
	private transient @Nullable Notification notification;	// Cache of remote notification to avoid expensive duplicate fetch.

    private static final int PARCEL_MAGIC = "NEVO".hashCode();  // TODO: Are they really magic enough?
	private static final Notification NULL_NOTIFICATION = new Notification();	// Must be placed before VOID to avoid NPE.
	private static final String TAG = "Nevo.Sbn";
}
