/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/** A {@link SqlSessionFactory} that counts the sessions opened and closed through it. */
final class CountingSessionFactory {

  private final AtomicInteger opened = new AtomicInteger();
  private final AtomicInteger closed = new AtomicInteger();
  private final SqlSessionFactory factory;

  CountingSessionFactory(final SqlSessionFactory delegate) {
    factory =
        (SqlSessionFactory)
            Proxy.newProxyInstance(
                SqlSessionFactory.class.getClassLoader(),
                new Class<?>[] {SqlSessionFactory.class},
                (proxy, method, args) -> {
                  final Object result = invoke(delegate, method, args);
                  if (method.getName().equals("openSession")) {
                    opened.incrementAndGet();
                    return countClose((SqlSession) result);
                  }
                  return result;
                });
  }

  private SqlSession countClose(final SqlSession session) {
    return (SqlSession)
        Proxy.newProxyInstance(
            SqlSession.class.getClassLoader(),
            new Class<?>[] {SqlSession.class},
            (proxy, method, args) -> {
              if (method.getName().equals("close")) {
                closed.incrementAndGet();
              }
              return invoke(session, method, args);
            });
  }

  private static Object invoke(final Object target, final Method method, final Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (final InvocationTargetException e) {
      throw e.getCause();
    }
  }

  int closed() {
    return closed.get();
  }

  SqlSessionFactory factory() {
    return factory;
  }

  int opened() {
    return opened.get();
  }

  void reset() {
    opened.set(0);
    closed.set(0);
  }
}
