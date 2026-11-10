/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;

public final class ZeebeTransactionIsolationTest extends ZeebeTransactionIsolationContractTest {

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createFactory() {
    return DefaultZeebeDbFactory.getDefaultFactory();
  }
}
