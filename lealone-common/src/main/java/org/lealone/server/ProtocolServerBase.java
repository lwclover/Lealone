/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;

import org.lealone.common.security.EncryptionOptions.ServerEncryptionOptions;
import org.lealone.db.Constants;

public abstract class ProtocolServerBase implements ProtocolServer {

    protected Map<String, String> config;
    protected String host = Constants.DEFAULT_HOST;
    protected int port;

    protected String baseDir;

    protected boolean ssl;
    protected boolean allowOthers;
    protected boolean isDaemon;
    protected boolean stopped;
    protected boolean started;
    protected boolean runInMainThread;

    // 如果allowOthers为false，那么可以指定具体的白名单，只有在白名单中的客户端才可以连进来
    protected HashSet<String> whiteList;
    protected ServerEncryptionOptions serverEncryptionOptions;
    protected int sessionTimeout = 60 * 1000; // 如果session在60秒内不活跃就会超时

    protected ProtocolServerBase() {
    }

    protected ProtocolServerBase(int port) {
        this.port = port;
    }

    @Override
    public void init(Map<String, String> config) { // TODO 对于不支持的参数直接报错
        this.config = config;
        if (config.containsKey("host"))
            host = config.get("host");
        if (config.containsKey("port"))
            port = Integer.parseInt(config.get("port"));

        baseDir = config.get("base_dir");

        ssl = Boolean.parseBoolean(config.get("ssl"));
        allowOthers = Boolean.parseBoolean(config.get("allow_others"));
        isDaemon = Boolean.parseBoolean(config.get("daemon"));
        runInMainThread = Boolean.parseBoolean(config.get("__runInMainThread__")); // 这个参数不对外

        if (config.containsKey("white_list")) {
            String[] hosts = config.get("white_list").split(",");
            whiteList = new HashSet<>(hosts.length);
            for (String host : hosts) {
                whiteList.add(host);
            }
        }
        if (config.containsKey("session_timeout"))
            sessionTimeout = Integer.parseInt(config.get("session_timeout"));
    }

    @Override
    public synchronized void start() {
        started = true;
        stopped = false;
    }

    @Override
    public synchronized void stop() {
        started = false;
        stopped = true;
    }

    @Override
    public synchronized boolean isRunning(boolean traceError) {
        return started && !stopped;
    }

    @Override
    public String getURL() {
        return (ssl ? "ssl" : getType()) + "://" + getHost() + ":" + port;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getType() {
        return getName();
    }

    @Override
    public boolean getAllowOthers() {
        return allowOthers;
    }

    @Override
    public boolean isDaemon() {
        return isDaemon;
    }

    /**
    * Get the configured base directory.
    *
    * @return the base directory
    */
    @Override
    public String getBaseDir() {
        return baseDir;
    }

    @Override
    public void setServerEncryptionOptions(ServerEncryptionOptions options) {
        this.serverEncryptionOptions = options;
    }

    @Override
    public ServerEncryptionOptions getServerEncryptionOptions() {
        return serverEncryptionOptions;
    }

    @Override
    public boolean isSSL() {
        return ssl;
    }

    @Override
    public Map<String, String> getConfig() {
        return config;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public boolean runInMainThread() {
        return runInMainThread;
    }

    @Override
    public boolean allow(String testHost) {
        if (allowOthers) {
            return true;
        }
        try {
            if (whiteList != null && whiteList.contains(testHost))
                return true;

            InetAddress localhost = InetAddress.getLocalHost();
            // localhost.getCanonicalHostName() is very very slow
            String host = localhost.getHostAddress();
            if (testHost.equals(host)) {
                return true;
            }

            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (testHost.equals(addr.getHostAddress())) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Override
    public int getSessionTimeout() {
        return sessionTimeout;
    }
}
