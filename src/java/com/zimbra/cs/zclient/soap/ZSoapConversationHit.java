/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.zclient.soap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.cs.zclient.ZConversationHit;
import com.zimbra.cs.zclient.ZEmailAddress;
import com.zimbra.soap.Element;

class ZSoapConversationHit implements ZConversationHit {

    private String mId;
    private String mFlags;
    private String mFragment;
    private String mSubject;
    private String mSortField;
    private String mTags;
    private int mMessageCount;
    private long mDate;
    private List<String> mMessageIds;
    private List<ZEmailAddress> mRecipients;
        
    ZSoapConversationHit(Element e, Map<String,ZSoapEmailAddress> cache) throws ServiceException {
        mId = e.getAttribute(MailService.A_ID);
        mFlags = e.getAttribute(MailService.A_FLAGS, "");
        mDate = e.getAttributeLong(MailService.A_DATE);
        mTags = e.getAttribute(MailService.A_TAGS, "");
        mFragment = e.getElement(MailService.E_FRAG).getText();
        mSubject = e.getElement(MailService.E_SUBJECT).getText();        
        mSortField = e.getAttribute(MailService.A_SORT_FIELD, null);
        mMessageCount = (int) e.getAttributeLong(MailService.A_NUM);
        mMessageIds = new ArrayList<String>();
        for (Element m: e.listElements(MailService.E_MSG)) {
            mMessageIds.add(m.getAttribute(MailService.A_ID));
        }
        
        mRecipients = new ArrayList<ZEmailAddress>();
        for (Element emailEl: e.listElements(MailService.E_EMAIL)) {
            mRecipients.add(ZSoapEmailAddress.getAddress(emailEl, cache));
        }        
    }

    public String getId() {
        return mId;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (ZEmailAddress addr : mRecipients) {
            sb.append("\n").append(addr);
        }
        sb.append("}");        
        
        return String.format("convhit: { id: %s, flags: %s, fragment: %s, subject: %s, date: %s, sortfield: %s, msgcount: %d, msgIds: %s, recipients: %s }",
                mId, mFlags, mFragment, mSubject, new Date(mDate), mSortField, mMessageCount, mMessageIds, sb.toString()); 
    }

    public String getFlags() {
        return mFlags;
    }

    public long getDate() {
        return mDate;
    }

    public String getFragment() {
        return mFragment;
    }

    public String getSortFied() {
        return mSortField;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getTagIds() {
        return mTags;
    }
    
    public int getMessageCount() {
        return mMessageCount;
    }

    public List<String> getMatchedMessageIds() {
        return mMessageIds;
    }

    public List<ZEmailAddress> getRecipients() {
        return mRecipients;
    }
}
