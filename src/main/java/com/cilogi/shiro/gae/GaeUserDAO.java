// Copyright (c) 2011 Tim Niblett All Rights Reserved.
//
// File:        GaeUserDAO.java  (01-Nov-2011)
// Author:      tim

//
// Copyright in the whole and every part of this source file belongs to
// Tim Niblett (the Author) and may not be used,
// sold, licenced, transferred, copied or reproduced in whole or in
// part in any manner or form or in or on any media to any person
// other than in accordance with the terms of The Author's agreement
// or otherwise without the prior written consent of The Author.  All
// information contained in this source file is confidential information
// belonging to The Author and as such may not be disclosed other
// than in accordance with the terms of The Author's agreement, or
// otherwise, without the prior written consent of The Author.  As
// confidential information this source file must be kept fully and
// effectively secure at all times.
//


package com.cilogi.shiro.gae;

import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.googlecode.objectify.ObjectifyService.ofy;

public class GaeUserDAO extends BaseDAO<GaeUser> {
    static final Logger LOG = Logger.getLogger(GaeUserDAO.class.getName());
    
    private static final long REGISTRATION_VALID_DAYS = 1;

    static {
        ObjectifyService.register(GaeUser.class);
        ObjectifyService.register(GaeUserCounter.class);
        ObjectifyService.register(RegistrationString.class);
    }

    public GaeUserDAO() {
        super(GaeUser.class);
    }

    /**
     * Save user with authorization information
     * @param user  User
     * @param changeCount should the user count be incremented
     * @return the user, after changes
     */
    public GaeUser saveUser(final GaeUser user, final boolean changeCount) {
        return ofy().transact(new Work<GaeUser>() {
            public GaeUser run() {
                put(user);
                if (changeCount) {
                    changeCount(1L);
                }
                return user;
            }
        });
    }

    public GaeUser deleteUser(final GaeUser user) {
        return ofy().transact(new Work<GaeUser>() {
             public GaeUser run() {
                delete(user.getName());
                changeCount(-1L);
                return user;
             }
        });
    }

    public RegistrationString saveRegistration(String registrationString, String userName) {
        RegistrationString reg = new RegistrationString(registrationString, userName, REGISTRATION_VALID_DAYS, TimeUnit.DAYS);
        new RegistrationDAO().put(reg);
        return reg;
    }

    public String findUserNameFromValidCode(String code) {
        RegistrationDAO dao = new RegistrationDAO();
        RegistrationString reg = dao.get(code);
        return (reg == null) ?  null : (reg.isValid() ? reg.getUsername() : null);
    }

    public GaeUser findUser(String userName) {
        return get(userName);
    }

    /**
     * Given a registration we have to retrieve it, and if its valid
     * update the associated user and then delete the registration.  This isn't
     * transactional and we may end up with a dangling RegistrationString, which
     * I can't see as too much of a problem, although they will need to be cleaned up with
     * a task on a regular basis (after they expire)..
     * @param code  The registration code
     * @param userName the user name for the code
     */
    public void register(final String code, final String userName) {
        ofy().transact(new VoidWork() {
            public void vrun() {
                GaeUser user = get(userName);
                if (user != null) {
                    user.register();
                    saveUser(user, true);
                }
                RegistrationDAO dao = new RegistrationDAO();
                RegistrationString reg = dao.get(code);
                if (reg != null) {
                    dao.delete(code);
                }
            }
        });
    }

    public long getCount() {
        GaeUserCounterDAO dao = new GaeUserCounterDAO();
        GaeUserCounter count = dao.get(GaeUserCounter.COUNTER_ID);
        return (count == null) ? 0 : count.getCount();
    }

    /**
     * Change the user count.
     * @param delta amount to change
     */
    private void changeCount(final long delta) {
        GaeUserCounterDAO dao = new GaeUserCounterDAO();
        GaeUserCounter count = dao.get(GaeUserCounter.COUNTER_ID);
        if (count == null) {
            count = new GaeUserCounter(GaeUserCounter.COUNTER_ID);
        }
        count.delta(delta);
        dao.put(count);
    }
}
