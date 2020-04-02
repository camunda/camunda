/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.session.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.Events;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

/** Client session. */
public class ClientSession<C> implements Session<C> {
  private final Session session;
  private final SessionProxy proxy;

  public ClientSession(final Class<C> clientType, final Session session) {
    this.session = checkNotNull(session);
    this.proxy = new SessionProxy(clientType);
  }

  @Override
  public SessionId sessionId() {
    return session.sessionId();
  }

  @Override
  public String primitiveName() {
    return session.primitiveName();
  }

  @Override
  public PrimitiveType primitiveType() {
    return session.primitiveType();
  }

  @Override
  public MemberId memberId() {
    return session.memberId();
  }

  @Override
  public State getState() {
    return session.getState();
  }

  @Override
  public <T> void publish(final EventType eventType, final T event) {
    session.publish(eventType, event);
  }

  @Override
  public void publish(final PrimitiveEvent event) {
    session.publish(event);
  }

  @Override
  public void accept(final Consumer<C> event) {
    proxy.accept(event);
  }

  /** Session proxy. */
  private final class SessionProxy {
    private final C proxy;

    @SuppressWarnings("unchecked")
    SessionProxy(final Class<C> clientType) {
      proxy =
          clientType == null
              ? null
              : (C)
                  java.lang.reflect.Proxy.newProxyInstance(
                      clientType.getClassLoader(),
                      new Class[] {clientType},
                      new SessionProxyHandler(clientType));
    }

    /**
     * Publishes an event to the session.
     *
     * @param event the event to publish
     */
    void accept(final Consumer<C> event) {
      event.accept(proxy);
    }
  }

  /** Session proxy invocation handler. */
  private final class SessionProxyHandler implements InvocationHandler {
    private final Map<Method, EventType> events;

    private SessionProxyHandler(final Class<C> clientType) {
      this.events = clientType != null ? Events.getMethodMap(clientType) : Maps.newHashMap();
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
        throws Throwable {
      final EventType eventType = events.get(method);
      if (eventType == null) {
        throw new PrimitiveException.ServiceException(
            "Cannot invoke unknown event type: " + method.getName());
      }
      session.publish(eventType, args);
      return null;
    }
  }
}
