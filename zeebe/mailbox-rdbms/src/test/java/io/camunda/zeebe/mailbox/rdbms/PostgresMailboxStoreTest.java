/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreContractTest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;

final class PostgresMailboxStoreTest extends MailboxStoreContractTest {

  private static SqlSessionFactory sessionFactory;
  private final AtomicReference<Duration> clockOffset = new AtomicReference<>(Duration.ZERO);

  @BeforeAll
  static void createDatabase() {
    sessionFactory = RdbmsMailboxStores.createSessionFactory(TestDatabases.postgres());
  }

  @Override
  protected MailboxStore createStore() {
    return new MyBatisMailboxStore(sessionFactory, clockOffset::get);
  }

  @Override
  protected void advanceTime(final Duration duration) {
    clockOffset.updateAndGet(offset -> offset.plus(duration));
  }
}
