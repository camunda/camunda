/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStoreContractTest;

final class InMemoryLeaseStoreTest extends LeaseStoreContractTest {

  @Override
  protected LeaseStore createStore() {
    return new InMemoryLeaseStore();
  }
}
