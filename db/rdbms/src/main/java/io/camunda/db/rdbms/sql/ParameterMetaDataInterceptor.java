/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLFeatureNotSupportedException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * Stops MyBatis from asking the JDBC driver for {@link java.sql.ParameterMetaData} while binding
 * parameters.
 *
 * <p>Since MyBatis 3.6.0, the {@code DefaultParameterHandler} calls {@code
 * PreparedStatement#getParameterMetaData()} for every parameter that has no explicit {@code
 * jdbcType}. That costs an extra round trip on some drivers, and the Oracle driver fails on several
 * of our statements ({@code XMLTYPE(?)}, {@code ROWNUM <= ?}) with an unchecked {@code
 * ArrayIndexOutOfBoundsException} or {@code NullPointerException}, which MyBatis does not catch.
 * MyBatis only tolerates a {@link java.sql.SQLException}, after which it falls back to the behavior
 * of earlier versions. This interceptor makes that fallback the one that is always taken.
 *
 * <p>Temporary: once MyBatis 3.6.1 is released, delete this class and call {@code
 * configuration.setIgnoreParameterMetaData(true)} instead, which does the same thing. Tracked in
 * camunda/camunda#69167.
 */
@Intercepts(
    @Signature(
        type = ParameterHandler.class,
        method = "setParameters",
        args = {PreparedStatement.class}))
public class ParameterMetaDataInterceptor implements Interceptor {

  @Override
  public Object intercept(final Invocation invocation) throws Throwable {
    final var delegate = (PreparedStatement) invocation.getArgs()[0];
    invocation.getArgs()[0] =
        Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              if ("getParameterMetaData".equals(method.getName())) {
                throw new ParameterMetaDataDisabledException();
              }
              try {
                return method.invoke(delegate, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
    return invocation.proceed();
  }

  /**
   * Thrown on every statement execution, so it skips the stack trace: MyBatis swallows it and the
   * trace would be deep and never read.
   */
  private static final class ParameterMetaDataDisabledException
      extends SQLFeatureNotSupportedException {

    ParameterMetaDataDisabledException() {
      super("Parameter metadata lookup is disabled for MyBatis parameter binding", null, 0, null);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
