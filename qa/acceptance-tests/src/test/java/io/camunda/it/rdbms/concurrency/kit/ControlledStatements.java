/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * MyBatis plugin that gives every thread started by {@link ThreadControl} a sync point before each
 * statement and before commit. Threads without a ThreadControl are not affected.
 *
 * <p>A JDBC batch (the exporter flush) is executed entry by entry, with a sync point before each
 * entry (see {@link SyncPoint#entry()}). The database sees the same statements in the same order,
 * one round trip per entry instead of one per batch, so a test can stop a flush between two rows of
 * the same statement.
 */
@Intercepts({
  @Signature(
      type = StatementHandler.class,
      method = "prepare",
      args = {Connection.class, Integer.class}),
  @Signature(
      type = Executor.class,
      method = "commit",
      args = {boolean.class})
})
public final class ControlledStatements implements Interceptor {

  static final ThreadLocal<ThreadControl> CONTROL = new ThreadLocal<>();

  @Override
  public Object intercept(final Invocation invocation) throws Throwable {
    final ThreadControl control = CONTROL.get();
    if (control == null) {
      return invocation.proceed();
    }
    if (invocation.getTarget() instanceof Executor) {
      control.sync(SyncPoint.commitPoint());
      return invocation.proceed();
    }
    final Statement statement = (Statement) invocation.proceed();
    final var handler = (StatementHandler) invocation.getTarget();
    final String sql = handler.getBoundSql().getSql().replaceAll("\\s+", " ").strip();
    final var stepper = new BatchStepper(control, statement, statementId(handler), sql);
    return Proxy.newProxyInstance(
        Statement.class.getClassLoader(), interfaces(statement.getClass()), stepper::invoke);
  }

  private static String statementId(final StatementHandler handler) {
    final var meta = SystemMetaObject.forObject(handler);
    return meta.hasGetter("delegate.mappedStatement")
        ? ((MappedStatement) meta.getValue("delegate.mappedStatement")).getId()
        : "";
  }

  private static Class<?>[] interfaces(final Class<?> type) {
    final Set<Class<?>> result = new LinkedHashSet<>();
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      collect(c, result);
    }
    return result.toArray(new Class<?>[0]);
  }

  private static void collect(final Class<?> type, final Set<Class<?>> result) {
    for (final Class<?> i : type.getInterfaces()) {
      if (result.add(i)) {
        collect(i, result);
      }
    }
  }

  /** Records the parameters of each batch entry and executes the entries one by one. */
  private static final class BatchStepper {
    private final ThreadControl control;
    private final Statement statement;
    private final String statementId;
    private final String sql;

    /** The latest setter per parameter index; JDBC keeps parameters across addBatch. */
    private final Map<Integer, Object[]> current = new LinkedHashMap<>();

    private final List<List<Object[]>> entries = new ArrayList<>();

    BatchStepper(
        final ThreadControl control,
        final Statement statement,
        final String statementId,
        final String sql) {
      this.control = control;
      this.statement = statement;
      this.statementId = statementId;
      this.sql = sql;
    }

    Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
      final String name = method.getName();
      if (statement instanceof final PreparedStatement ps) {
        if (isParameterSetter(method)) {
          // passed on at once as well: type handlers may release a value right after setting it
          // (the PostgreSQL array handler frees its array)
          current.put((Integer) args[0], new Object[] {method, args});
        } else if (name.equals("clearParameters")) {
          current.clear();
        } else if (name.equals("addBatch") && (args == null || args.length == 0)) {
          entries.add(new ArrayList<>(current.values()));
          return null;
        } else if (name.equals("clearBatch")) {
          entries.clear();
          return null;
        } else if (name.equals("executeBatch")) {
          return executeEntries(ps);
        }
      }
      if (name.startsWith("execute")) {
        control.sync(SyncPoint.of(statementId, sql, 1, 1));
        try {
          return call(statement, method, args);
        } catch (final SQLException e) {
          control.statementFailed(e);
          throw e;
        }
      }
      return call(statement, method, args);
    }

    private int[] executeEntries(final PreparedStatement ps) throws Throwable {
      final int n = entries.size();
      final int[] counts = new int[n];
      for (int i = 0; i < n; i++) {
        control.sync(SyncPoint.of(statementId, sql, i + 1, n));
        // a single entry's parameters are still set; replaying them would bind a value twice,
        // which e.g. Oracle rejects for character streams (ORA-17270). Stepping through several
        // entries replays each entry's setters (not supported for released or stream values).
        if (n > 1) {
          ps.clearParameters();
          for (final Object[] setter : entries.get(i)) {
            call(ps, (Method) setter[0], (Object[]) setter[1]);
          }
        }
        try {
          counts[i] = ps.executeUpdate();
        } catch (final SQLException e) {
          control.statementFailed(e);
          throw e;
        }
      }
      entries.clear();
      return counts;
    }

    private static boolean isParameterSetter(final Method method) {
      return method.getName().startsWith("set")
          && method.getParameterCount() >= 2
          && method.getParameterTypes()[0] == int.class
          && method.getDeclaringClass() != Statement.class;
    }

    private static Object call(final Object target, final Method method, final Object[] args)
        throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (final InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
