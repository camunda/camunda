/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.function.Function;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.jspecify.annotations.NullMarked;

/**
 * A physical tenant's session factory, which builds the tenant's MyBatis on first use and holds it
 * for as long as a session is open.
 */
@NullMarked
final class LazySqlSessionFactory implements SqlSessionFactory {

  private final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis;

  LazySqlSessionFactory(final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis) {
    this.myBatis = myBatis;
  }

  @Override
  public SqlSession openSession() {
    return leased(factory -> factory.openSession());
  }

  @Override
  public SqlSession openSession(final boolean autoCommit) {
    return leased(factory -> factory.openSession(autoCommit));
  }

  @Override
  public SqlSession openSession(final Connection connection) {
    return leased(factory -> factory.openSession(connection));
  }

  @Override
  public SqlSession openSession(final TransactionIsolationLevel level) {
    return leased(factory -> factory.openSession(level));
  }

  @Override
  public SqlSession openSession(final ExecutorType execType) {
    return leased(factory -> factory.openSession(execType));
  }

  @Override
  public SqlSession openSession(final ExecutorType execType, final boolean autoCommit) {
    return leased(factory -> factory.openSession(execType, autoCommit));
  }

  @Override
  public SqlSession openSession(
      final ExecutorType execType, final TransactionIsolationLevel level) {
    return leased(factory -> factory.openSession(execType, level));
  }

  @Override
  public SqlSession openSession(final ExecutorType execType, final Connection connection) {
    return leased(factory -> factory.openSession(execType, connection));
  }

  @Override
  public Configuration getConfiguration() {
    return myBatis.use(current -> current.sqlSessionFactory().getConfiguration());
  }

  /**
   * Opens a session that holds the tenant's MyBatis until it is closed, so that the MyBatis and its
   * pool are not released under it.
   */
  private SqlSession leased(final Function<SqlSessionFactory, SqlSession> open) {
    final var lease = myBatis.acquire();
    try {
      final var session = open.apply(lease.resource().sqlSessionFactory());
      return (SqlSession)
          Proxy.newProxyInstance(
              SqlSession.class.getClassLoader(),
              new Class<?>[] {SqlSession.class},
              (proxy, method, args) -> {
                try {
                  return method.invoke(session, args);
                } catch (final InvocationTargetException e) {
                  throw e.getCause();
                } finally {
                  if (method.getName().equals("close")) {
                    lease.close();
                  }
                }
              });
    } catch (final RuntimeException e) {
      lease.close();
      throw e;
    }
  }
}
