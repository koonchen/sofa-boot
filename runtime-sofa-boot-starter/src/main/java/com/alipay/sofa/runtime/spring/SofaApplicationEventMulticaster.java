/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.runtime.spring;

import com.alipay.sofa.healthcheck.startup.ReadinessCheckListener;
import com.alipay.sofa.infra.listener.SofaBootstrapRunListener;
import com.alipay.sofa.runtime.spring.async.AsyncTaskExecutionListener;
import com.alipay.sofa.runtime.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * @author qilong.zql
 */
public class SofaApplicationEventMulticaster extends SimpleApplicationEventMulticaster {

    private Set<String>   syncListener;
    private static String SYNC_LISTENER = "sofa.sync.listener";
    private static String COMMA         = ",";

    @Autowired
    private Environment   environment;

    @Override
    public void multicastEvent(ApplicationEvent event, ResolvableType eventType) {
        if (syncListener == null) {
            syncListener = new HashSet<>();
            String listeners = environment.getProperty(SYNC_LISTENER);
            if (StringUtils.hasText(listeners)) {
                for (String listener : listeners.split(COMMA)) {
                    syncListener.add(listener.trim());
                }
            }
        }
        ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
        for (final ApplicationListener<?> listener : getApplicationListeners(event, type)) {
            Executor executor = getTaskExecutor();
            if (executor != null && isAsyncListener(listener)) {
                executor.execute(() -> invokeListener(listener, event));
            }
            else {
                invokeListener(listener, event);
            }
        }
    }

    private ResolvableType resolveDefaultEventType(ApplicationEvent event) {
        return ResolvableType.forInstance(event);
    }

    private boolean isAsyncListener(ApplicationListener listener) {
        if (listener instanceof SofaBootstrapRunListener) {
            return false;
        }

        if (listener instanceof ReadinessCheckListener) {
            return false;
        }

        if (listener instanceof AsyncTaskExecutionListener) {
            return false;
        }

        if (listener.getClass().getCanonicalName()
            .equals("com.alipay.sofa.isle.spring.listener.SofaModuleContextRefreshedListener")) {
            return false;
        }

        for (String sync : syncListener) {
            if (listener.getClass().getCanonicalName().equals(sync)) {
                return false;
            }
        }

        return true;
    }
}