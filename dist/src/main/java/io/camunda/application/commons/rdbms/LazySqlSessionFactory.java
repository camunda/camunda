/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import java.sql.Connection;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.jspecify.annotations.NullMarked;

/** A physical tenant's session factory, which builds the tenant's MyBatis on first use. */
@NullMarked
final class LazySqlSessionFactory implements SqlSessionFactory {

  private final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis;

  LazySqlSessionFactory(final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis) {
    this.myBatis = myBatis;
  }

  @Override
  public SqlSession openSession() {
    return delegate().openSession();
  }

  @Override
  public SqlSession openSession(final boolean autoCommit) {
    return delegate().openSession(autoCommit);
  }

  @Override
  public SqlSession openSession(final Connection connection) {
    return delegate().openSession(connection);
  }

  @Override
  public SqlSession openSession(final TransactionIsolationLevel level) {
    return delegate().openSession(level);
  }

  @Override
  public SqlSession openSession(final ExecutorType execType) {
    return delegate().openSession(execType);
  }

  @Override
  public SqlSession openSession(final ExecutorType execType, final boolean autoCommit) {
    return delegate().openSession(execType, autoCommit);
  }

  @Override
  public SqlSession openSession(
      final ExecutorType execType, final TransactionIsolationLevel level) {
    return delegate().openSession(execType, level);
  }

  @Override
  public SqlSession openSession(final ExecutorType execType, final Connection connection) {
    return delegate().openSession(execType, connection);
  }

  @Override
  public Configuration getConfiguration() {
    return delegate().getConfiguration();
  }

  private SqlSessionFactory delegate() {
    return myBatis.get().sqlSessionFactory();
  }
}
