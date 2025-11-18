/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DecisionInstanceWriterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final Duration CLEANUP_DURATION = Duration.ofDays(10);

  private final DecisionInstanceMapper decisionInstanceMapper =
      Mockito.mock(DecisionInstanceMapper.class);
  private final ExecutionQueue executionQueue = Mockito.mock(ExecutionQueue.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      Mockito.mock(VendorDatabaseProperties.class);
  private final RdbmsWriterConfig rdbmsWriterConfig =
      Mockito.mock(RdbmsWriterConfig.class, Mockito.RETURNS_DEEP_STUBS);

  private final DecisionInstanceWriter decisionInstanceWriter =
      new DecisionInstanceWriter(
          decisionInstanceMapper, executionQueue, vendorDatabaseProperties, rdbmsWriterConfig);

  @BeforeEach
  public void init() {
    Mockito.when(rdbmsWriterConfig.history().decisionInstanceTTL()).thenReturn(CLEANUP_DURATION);
  }

  @Test
  void shouldNotSetHistoryCleanupWhenDecisionInstanceCreatedWithProcessInstance() {
    final var model =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("decision-instance-1")
            .processInstanceKey(1L)
            .evaluationDate(NOW)
            .build();

    decisionInstanceWriter.create(model);

    final var queueItemCaptor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue).executeInQueue(queueItemCaptor.capture());
    final var capturedModel = (DecisionInstanceDbModel) queueItemCaptor.getValue().parameter();

    assertThat(capturedModel.historyCleanupDate()).isNull();
  }

  @Test
  void shouldSetHistoryCleanupWhenDecisionInstanceCreatedWithoutProcessInstance() {
    final var model =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("decision-instance-1")
            .evaluationDate(NOW)
            .build();

    decisionInstanceWriter.create(model);

    final var queueItemCaptor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue).executeInQueue(queueItemCaptor.capture());
    final var capturedModel = (DecisionInstanceDbModel) queueItemCaptor.getValue().parameter();

    assertThat(capturedModel.historyCleanupDate()).isEqualTo(NOW.plus(CLEANUP_DURATION));
  }

  @Test
  void shouldSetHistoryCleanupWhenDecisionInstanceCreatedWithAbsentProcessInstance() {
    final var model =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("decision-instance-1")
            .processInstanceKey(-1L)
            .evaluationDate(NOW)
            .build();

    decisionInstanceWriter.create(model);

    final var queueItemCaptor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue).executeInQueue(queueItemCaptor.capture());
    final var capturedModel = (DecisionInstanceDbModel) queueItemCaptor.getValue().parameter();

    assertThat(capturedModel.historyCleanupDate()).isEqualTo(NOW.plus(CLEANUP_DURATION));
  }
}
