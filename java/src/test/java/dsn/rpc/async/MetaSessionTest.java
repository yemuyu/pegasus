// Copyright (c) 2017, Xiaomi, Inc.  All rights reserved.
// This source code is licensed under the Apache License Version 2.0, which
// can be found in the LICENSE file in the root directory of this source tree.

package dsn.rpc.async;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import dsn.operator.*;
import dsn.base.*;
import dsn.replication.query_cfg_request;

/** 
* MetaSession Tester. 
* 
* @author sunweijie@xiaomi.com
* @version 1.0 
*/ 
public class MetaSessionTest { 

    @Before
    public void before() throws Exception { 
    } 
    
    @After
    public void after() throws Exception {
        rpc_address addr = new rpc_address();
        addr.fromString("127.0.0.1:34602");
        dsn.tools.Toollet.tryStartServer(addr);
    }

    private static void ensureNotLeader(rpc_address addr) {
        dsn.tools.Toollet.closeServer(addr);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        dsn.tools.Toollet.tryStartServer(addr);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method: connect() 
     */ 
    @Test
    public void testConnect() throws Exception {
        // test: first connect to a wrong server
        // then it forward to the right server
        // then the wrong server crashed

        String[] addr_list = {"127.0.0.1:34602", "127.0.0.1:34603", "127.0.0.1:34601"};
        ClusterManager manager = new ClusterManager(1000, 4, null, addr_list);
        MetaSession session = manager.getMetaSession();

        rpc_address addr = new rpc_address();
        addr.fromString("127.0.0.1:34602");
        ensureNotLeader(addr);

        ArrayList<FutureTask<Void>> callbacks = new ArrayList<FutureTask<Void>>();
        for (int i=0; i<1000; ++i) {
            query_cfg_request req = new query_cfg_request("temp", new ArrayList<Integer>());
            final client_operator op = new query_cfg_operator(new gpid(-1, -1), req);
            FutureTask<Void> callback = new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Assert.assertEquals(error_code.error_types.ERR_OK, op.rpc_error.errno);
                    return null;
                }
            });
            callbacks.add(callback);
            session.asyncQuery(op, callback, 10);
        }

        dsn.tools.Toollet.closeServer(addr);
        for (FutureTask<Void> cb: callbacks) {
            try {
                dsn.utils.tools.waitUninterruptable(cb, Integer.MAX_VALUE);
            } catch (ExecutionException e) {
                e.printStackTrace();
                Assert.fail();
            }
        }
    }
}
