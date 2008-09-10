/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.jcrinstall.jcr.impl;

import java.util.Set;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jcrinstall.osgi.OsgiController;

/** Slightly customized RepositoryObserver
 *  used for testing.
 */
public class MockRepositoryObserver extends RepositoryObserver {
    MockRepositoryObserver(SlingRepository repo, final OsgiController c) {
        repository = repo;
        osgiController = c;
        scanDelayMsec = 0;
    }
    
    public void run() {
        // Do not run the observation cycle - we do that ourselves in testing
    }
    
    Set<WatchedFolder> getWatchedFolders() {
        return folders;
    }
    
    boolean folderIsWatched(String path) throws Exception {
        boolean result = false;
        for(WatchedFolder wf : folders) {
            if(wf.getPath().equals("/" + path)) {
                result = true;
                break;
            }
        }
        return result;
    }
}
