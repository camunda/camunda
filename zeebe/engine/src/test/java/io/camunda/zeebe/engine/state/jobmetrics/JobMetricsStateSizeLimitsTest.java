/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.file.Files;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;

/** Runs the job metrics size limits contract against the RocksDB state. */
final class JobMetricsStateSizeLimitsTest extends AbstractJobMetricsStateSizeLimitsContractTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  @BeforeEach
  void setUp() throws Exception {
    final var factory = DefaultZeebeDbFactory.defaultFactory();
    zeebeDb = factory.createDb(Files.createTempDirectory(null).toFile());
    transactionContext = zeebeDb.createContext();
  }

  @Override
  protected MutableJobMetricsState createJobMetricsState(final EngineConfiguration config) {
    return new JobMetricsStateImpl(
        new DbJobMetricsStore(zeebeDb, transactionContext), InstantSource.system(), config);
  }
}
