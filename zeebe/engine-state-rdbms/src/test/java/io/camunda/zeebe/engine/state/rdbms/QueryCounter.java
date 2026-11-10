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
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the statements that connections execute outside of batches, i.e. the queries, with their
 * SQL. Decorate the connections of an {@link ObservedDataSource} with {@link #wrap}.
 */
final class QueryCounter {

  private final List<String> queries = new ArrayList<>();

  Connection wrap(final Connection target) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              final Object result = invoke(target, method, args);
              if (method.getName().equals("prepareStatement")
                  && result instanceof final PreparedStatement statement) {
                return counting((String) args[0], statement);
              }
              return result;
            });
  }

  /** Runs the operation and returns the queries it sent, as the first word after their FROM. */
  List<String> queriesOf(final Runnable operation) {
    queries.clear();
    operation.run();
    return List.copyOf(queries);
  }

  private PreparedStatement counting(final String sql, final PreparedStatement target) {
    return (PreparedStatement)
        Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              if (method.getName().startsWith("execute")
                  && !method.getName().equals("executeBatch")) {
                queries.add(table(sql));
              }
              return invoke(target, method, args);
            });
  }

  private static String table(final String sql) {
    final var words = sql.trim().split("\\s+");
    for (int i = 0; i < words.length - 1; i++) {
      if (words[i].equalsIgnoreCase("FROM")) {
        return words[i + 1];
      }
    }
    return sql;
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
