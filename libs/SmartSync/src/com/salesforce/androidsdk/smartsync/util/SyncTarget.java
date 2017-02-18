/*
 * Copyright (c) 2014-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.smartsync.util;

import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Abstract super class for SyncUpTarget and SyncDownTarget
 *
 * Targets handle interactions with local store and with remote server.
 *
 * Default targets use SmartStore for local store and __local_*__ fields to flag dirty (i.e. locally created/updated/deleted) records.
 * Custom targets can use a different local store and/or different fields to flag dirty records.
 *
 * Default targets use SObject Rest API to read/write records to the server.
 * Custom targets can use different end points to read/write records to the server.
 */
public abstract class SyncTarget {

    // Sync targets expect the following fields in locally stored records
    public static final String LOCALLY_CREATED = "__locally_created__";
    public static final String LOCALLY_UPDATED = "__locally_updated__";
    public static final String LOCALLY_DELETED = "__locally_deleted__";
    public static final String LOCAL = "__local__";

    // Page size used when reading from smartstore
    private static final int PAGE_SIZE = 2000;

    public static final String ANDROID_IMPL = "androidImpl";
    public static final String ID_FIELD_NAME = "idFieldName";
    public static final String MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName";

    private String idFieldName;
    private String modificationDateFieldName;

    public SyncTarget() {
        idFieldName = Constants.ID;
        modificationDateFieldName = Constants.LAST_MODIFIED_DATE;
    }

    public SyncTarget(JSONObject target) throws JSONException {
        idFieldName = target != null && target.has(ID_FIELD_NAME) ? target.getString(ID_FIELD_NAME) : Constants.ID;
        modificationDateFieldName = target != null && target.has(MODIFICATION_DATE_FIELD_NAME) ? target.getString(MODIFICATION_DATE_FIELD_NAME) : Constants.LAST_MODIFIED_DATE;
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = new JSONObject();
        target.put(ANDROID_IMPL, getClass().getName());
        target.put(ID_FIELD_NAME, idFieldName);
        target.put(MODIFICATION_DATE_FIELD_NAME, modificationDateFieldName);
        return target;
    }

    /**
     * @return The field name of the ID field of the record.  Defaults to "Id".
     */
    public String getIdFieldName() {
        return idFieldName;
    }

    /**
     * @return The field name of the modification date field of the record.  Defaults to "LastModifiedDate".
     */
    public String getModificationDateFieldName() {
        return modificationDateFieldName;
    }


    /**
     * Return ids of "dirty" records (records locally created/upated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @return
     * @throws JSONException
     */
    public SortedSet<String> getDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        SortedSet<String> ids = new TreeSet<String>();
        String dirtyRecordsSql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true' ORDER BY {%s:%s} ASC", soupName, idField, soupName, soupName, LOCAL, soupName, idField);
        final QuerySpec smartQuerySpec = QuerySpec.buildSmartQuerySpec(dirtyRecordsSql, PAGE_SIZE);
        boolean hasMore = true;
        for (int pageIndex = 0; hasMore; pageIndex++) {
            JSONArray results = syncManager.getSmartStore().query(smartQuerySpec, pageIndex);
            hasMore = (results.length() == PAGE_SIZE);
            ids.addAll(toSortedSet(results));
        }
        return ids;
    }

    /**
     * Return ids of non-dirty records (records NOT locally created/updated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @return
     * @throws JSONException
     */
    public SortedSet<String> getNonDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        SortedSet<String> ids = new TreeSet<String>();
        String nonDirtyRecordsSql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'false' ORDER BY {%s:%s} ASC", soupName, getIdFieldName(), soupName, soupName, LOCAL, soupName, idField);
        final QuerySpec smartQuerySpec = QuerySpec.buildSmartQuerySpec(nonDirtyRecordsSql, PAGE_SIZE);
        boolean hasMore = true;
        for (int pageIndex = 0; hasMore; pageIndex++) {
            JSONArray results = syncManager.getSmartStore().query(smartQuerySpec, pageIndex);
            hasMore = (results.length() == PAGE_SIZE);
            ids.addAll(toSortedSet(results));
        }
        return ids;
    }

    /**
     * Given a "dirty" record, return true if it was locally created
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyCreated(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_CREATED);
    }

    /**
     * Given a "dirty" record, return true if it was locally updated
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyUpdated(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_UPDATED);
    }

    /**
     * Given a "dirty" record, return true if it was locally deleted
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyDeleted(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_DELETED);
    }

    /**
     * Get record from local store by storeId
     * @param syncManager
     * @param storeId
     * @throws  JSONException
     */
    public JSONObject getFromLocalStore(SyncManager syncManager, String soupName, String storeId) throws JSONException {
        return syncManager.getSmartStore().retrieve(soupName, Long.valueOf(storeId)).getJSONObject(0);
    }

    /**
     * Clean (i.e. no longer flag as "dirty") and save record in local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    public void cleanAndSaveInLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        cleanAndSaveInLocalStore(syncManager, soupName, record, true);
    }

    private void cleanAndSaveInLocalStore(SyncManager syncManager, String soupName, JSONObject record, boolean handleTx) throws JSONException {
        record.put(LOCAL, false);
        record.put(LOCALLY_CREATED, false);
        record.put(LOCALLY_UPDATED, false);
        record.put(LOCALLY_DELETED, false);
        if (record.has(SmartStore.SOUP_ENTRY_ID)) {
            // Record came from smartstore
            syncManager.getSmartStore().update(soupName, record, record.getLong(SmartStore.SOUP_ENTRY_ID), handleTx);
        }
        else {
            // Record came from server
            syncManager.getSmartStore().upsert(soupName, record, getIdFieldName(), handleTx);
        }
    }

    /**
     * Save records to local store
     * @param syncManager
     * @param soupName
     * @param records
     * @throws JSONException
     */
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records) throws JSONException {
        SmartStore smartStore = syncManager.getSmartStore();
        synchronized(smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();
                for (int i = 0; i < records.length(); i++) {
                    cleanAndSaveInLocalStore(syncManager, soupName, records.getJSONObject(i), false);
                }
                smartStore.setTransactionSuccessful();
            }
            finally {
                smartStore.endTransaction();
            }
        }
    }

    /**
     * Delete record from local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    public void deleteFromLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        syncManager.getSmartStore().delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID));
    }

    /**
     * Delete the records with the given soup entry ids
     * @param syncManager
     * @param soupName
     * @param ids
     */
    public void deleteRecordsFromLocalStore(SyncManager syncManager, String soupName, Set<String> ids) {
        if (ids.size() > 0) {
            String smartSql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} IN (%s)",
                    soupName, SmartStore.SOUP_ENTRY_ID, soupName, soupName, getIdFieldName(),
                    "'" + TextUtils.join("', '", ids) + "'");
            QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, ids.size());
            syncManager.getSmartStore().deleteByQuery(soupName, querySpec);
        }
    }

    private SortedSet<String> toSortedSet(JSONArray jsonArray) throws JSONException {
        SortedSet<String> set = new TreeSet<String>();
        for (int i=0; i<jsonArray.length(); i++) {
            set.add(jsonArray.getJSONArray(i).getString(0));
        }
        return set;
    }
}