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
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;

/**
 * A {@link DataSource} that counts the connections borrowed from it and returned to it, and lets a
 * test decorate the connections it hands out, e.g. to log statements or to fail a commit. Stands in
 * for the pool of the engine state.
 */
final class ObservedDataSource {

  private final AtomicInteger borrowed = new AtomicInteger();
  private final AtomicInteger returned = new AtomicInteger();
  private volatile UnaryOperator<Connection> decorator = UnaryOperator.identity();
  private final DataSource dataSource;

  ObservedDataSource(final DataSource delegate) {
    dataSource =
        (DataSource)
            Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                  final Object result = invoke(delegate, method, args);
                  if (method.getName().equals("getConnection")) {
                    borrowed.incrementAndGet();
                    return countReturn(decorator.apply((Connection) result));
                  }
                  return result;
                });
  }

  private Connection countReturn(final Connection connection) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("close")) {
                returned.incrementAndGet();
              }
              return invoke(connection, method, args);
            });
  }

  DataSource dataSource() {
    return dataSource;
  }

  /** Decorates the connections borrowed from now on; the decorator sees the real connection. */
  void decorate(final UnaryOperator<Connection> decorator) {
    this.decorator = decorator;
  }

  int borrowed() {
    return borrowed.get();
  }

  int returned() {
    return returned.get();
  }

  /** Forgets the connections counted so far and removes the decorator. */
  void reset() {
    borrowed.set(0);
    returned.set(0);
    decorator = UnaryOperator.identity();
  }

  private static Object invoke(final Object target, final Method method, final Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (final InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
