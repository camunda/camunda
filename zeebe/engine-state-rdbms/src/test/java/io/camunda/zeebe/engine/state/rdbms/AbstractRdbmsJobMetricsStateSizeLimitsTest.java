/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.jobmetrics.AbstractJobMetricsStateSizeLimitsContractTest;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import java.time.InstantSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Runs the job metrics size limits contract against PostgreSQL. All tests share one {@link
 * EngineStateDatabase}; each test writes to its own partition.
 */
abstract class AbstractRdbmsJobMetricsStateSizeLimitsTest
    extends AbstractJobMetricsStateSizeLimitsContractTest {

  /** How the session under test writes: directly, or behind the commit. */
  abstract StateMode mode();

  private static EngineStateDatabase database;

  private JdbcTransactionContext transactionContext;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableJobMetricsState createJobMetricsState(final EngineConfiguration config) {
    transactionContext = new JdbcTransactionContext();
    final var session =
        database.entitySession(mode(), transactionContext, database.nextPartitionId());
    return new JobMetricsStateImpl(
        new RdbmsJobMetricsStore(session), InstantSource.system(), config);
  }

  @AfterEach
  void endTransaction() throws Exception {
    if (transactionContext != null) {
      transactionContext.getCurrentTransaction().rollback();
    }
  }
}
