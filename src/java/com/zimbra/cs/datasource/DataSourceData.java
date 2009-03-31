/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2009 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Yahoo! Public License
 * Version 1.0 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.datasource;

import java.util.ArrayList;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.db.DbDataSource;
import com.zimbra.cs.db.DbDataSource.DataSourceItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Metadata;

public class DataSourceData {
    protected DataSource ds;
    protected DataSourceItem dsi;
    
    public DataSourceData(DataSource ds, DataSourceItem dsi) throws ServiceException {
        this.ds = ds;
        this.dsi = dsi;
        parseMetaData();
    }
    
    public DataSourceData(DataSource ds, int itemId) throws ServiceException {
        this.ds = ds;
        dsi = DbDataSource.getMapping(ds, itemId);
        if (dsi.itemId == 0)
            throw MailServiceException.NO_SUCH_ITEM(itemId);
        parseMetaData();
    }
    
    public DataSourceData(DataSource ds, String remoteId) throws ServiceException {
        this.ds = ds;
        dsi = DbDataSource.getReverseMapping(ds, remoteId);
        if (dsi.itemId == 0)
            throw MailServiceException.NO_SUCH_ITEM(remoteId);
        parseMetaData();
    }
    
    public DataSourceData(DataSource ds, int localId, String remoteId) throws ServiceException {
        this.ds = ds;
        dsi = new DataSourceItem(localId, remoteId, new Metadata());
    }
    
    public int getItemId() { return dsi.itemId; }
    
    public String getRemoteId() { return dsi.remoteId; }
    
    public DataSource getDataSource() { return ds; }
    
    public DataSourceItem getDataSourceItem() { return dsi; }
    
    public void add() throws ServiceException {
        DbDataSource.addMapping(ds, dsi);
    }
    
    public void delete() throws ServiceException {
        delete(ds, dsi.itemId);
    }
    
    public void deleteFolder() throws ServiceException {
        deleteAllMappingsInFolder();
        delete();
    }
    
    public void deleteAllMappingsInFolder() throws ServiceException {
        deleteAllMappingsInFolder(ds, dsi.itemId);
    }
    
    public static void delete(DataSource ds, int itemId) throws ServiceException {
        ArrayList<Integer> toDelete = new ArrayList<Integer>(1);

        toDelete.add(itemId);
        DbDataSource.deleteMappings(ds, toDelete);
    }
    
    public static void deleteAllMappingsInFolder(DataSource ds, int itemId)
        throws ServiceException {
        DbDataSource.deleteAllMappingsInFolder(ds, itemId);
    }
    
    public void set() throws ServiceException {
        try {
            DbDataSource.addMapping(ds, dsi);
        } catch (Exception e) {
            delete();
            DbDataSource.addMapping(ds, dsi);
        }
    }
    
    public void update() throws ServiceException {
        DbDataSource.updateMapping(ds, dsi);
    }
    
    protected void parseMetaData() throws ServiceException {}
}
