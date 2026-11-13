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
import io.camunda.zeebe.db.writebehind.KeyIndexPolicy;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import java.util.Map;

/**
 * The write-behind DB must behave like plain RocksDB with a window over the highest keys of the
 * tested column family. The key range of the random operations is 8, so the window of 4 keys
 * evicts, refills and hands scans over to the storage in most runs; the read cache and the
 * pending-bytes budget are tiny, so that values are read from the storage.
 */
public final class RocksDbVsWriteBehindKeyIndexHighestWindowDifferentialTest
    extends DifferentialRandomizedContractTest {

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createReferenceFactory() {
    return RocksDbTestFactory.create();
  }

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createCandidateFactory() {
    return WriteBehindTestFactory.create(
        WriteBehindConfiguration.defaults()
            .withCacheMaxBytes(512)
            .withMaxPendingBytes(1024)
            .withKeyIndexMaxKeys(4)
            .withKeyIndexPolicies(Map.of(0L, KeyIndexPolicy.KEEP_HIGHEST)));
  }
}
