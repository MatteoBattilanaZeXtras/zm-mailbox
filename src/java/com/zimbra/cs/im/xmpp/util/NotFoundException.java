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
 * Part of the Zimbra Collaboration Suite Server.
 *
 * The Original Code is Copyright (C) Jive Software. Used with permission
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.im.xmpp.util;

/**
 * <p>Flags an exception when something requested is not found.</p>
 * <p>Use this class when it's not worth creating a unique xNotFoundException class, or
 * where the context of the call makes it obvious what type of object was not found.</p>
 *
 * @author Iain Shigeoka
 */
public class NotFoundException extends Exception {

    public NotFoundException() {
    }

    public NotFoundException(String message) {
        super(message);
    }
}
