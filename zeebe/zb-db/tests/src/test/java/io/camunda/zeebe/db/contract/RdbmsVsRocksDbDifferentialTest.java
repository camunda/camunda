/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;

/** The relational storage must always agree with plain RocksDB. */
public final class RdbmsVsRocksDbDifferentialTest extends DifferentialRandomizedContractTest {

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createReferenceFactory() {
    return RocksDbTestFactory.create();
  }

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createCandidateFactory() {
    return RdbmsTestFactory.create();
  }
}
