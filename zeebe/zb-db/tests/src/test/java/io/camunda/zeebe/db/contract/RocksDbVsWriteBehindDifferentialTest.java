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
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;

/**
 * The write-behind DB must behave like plain RocksDB. Its read cache and pending-bytes budget are
 * tiny, so that random operations keep hitting evictions and commits that wait for the writer.
 */
public final class RocksDbVsWriteBehindDifferentialTest extends DifferentialRandomizedContractTest {

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createReferenceFactory() {
    return RocksDbTestFactory.create();
  }

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createCandidateFactory() {
    return WriteBehindTestFactory.create(
        WriteBehindConfiguration.defaults().withCacheMaxBytes(512).withMaxPendingBytes(1024));
  }
}
