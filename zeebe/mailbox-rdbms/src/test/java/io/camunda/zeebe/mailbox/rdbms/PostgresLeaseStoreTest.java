/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStoreContractTest;
import org.junit.jupiter.api.BeforeAll;

final class PostgresLeaseStoreTest extends LeaseStoreContractTest {

  private static RdbmsMailboxStores stores;

  @BeforeAll
  static void createDatabase() {
    stores = RdbmsMailboxStores.create(TestDatabases.postgres());
  }

  @Override
  protected LeaseStore createStore() {
    return stores.leaseStore();
  }
}
