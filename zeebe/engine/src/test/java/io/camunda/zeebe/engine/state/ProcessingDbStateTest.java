/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState;
import io.camunda.zeebe.engine.state.jobmetrics.NoopJobMetricsState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessingDbStateTest {

  private Path tempFolder;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  @BeforeEach
  void setUp() throws Exception {
    tempFolder = Files.createTempDirectory(null);
    zeebeDb = DefaultZeebeDbFactory.defaultFactory().createDb(tempFolder.toFile());
    transactionContext = zeebeDb.createContext();
  }

  @Test
  void shouldUseDbJobMetricsStateWhenJobMetricsExportEnabled() {
    // given
    final var config = new EngineConfiguration().setJobMetricsExportEnabled(true);
    final var keyGenerator =
        new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);

    // when
    final var processingState =
        new ProcessingDbState(
            Protocol.DEPLOYMENT_PARTITION,
            zeebeDb,
            transactionContext,
            keyGenerator,
            new TransientPendingSubscriptionState(),
            new TransientPendingSubscriptionState(),
            config,
            InstantSource.system(),
            ExpressionLanguageMetrics.noop());

    // then
    assertThat(processingState.getJobMetricsState()).isInstanceOf(DbJobMetricsState.class);
  }

  @Test
  void shouldUseNoopJobMetricsStateWhenJobMetricsExportDisabled() {
    // given
    final var config = new EngineConfiguration().setJobMetricsExportEnabled(false);
    final var keyGenerator =
        new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);

    // when
    final var processingState =
        new ProcessingDbState(
            Protocol.DEPLOYMENT_PARTITION,
            zeebeDb,
            transactionContext,
            keyGenerator,
            new TransientPendingSubscriptionState(),
            new TransientPendingSubscriptionState(),
            config,
            InstantSource.system(),
            ExpressionLanguageMetrics.noop());

    // then
    assertThat(processingState.getJobMetricsState()).isInstanceOf(NoopJobMetricsState.class);
  }
}
