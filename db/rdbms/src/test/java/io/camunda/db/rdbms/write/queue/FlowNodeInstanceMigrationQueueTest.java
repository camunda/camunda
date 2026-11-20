/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A migration (ELEMENT_MIGRATED / ANCESTOR_MIGRATED) writes only the element and process definition
 * columns. Changes of the same element later in the same flush (completion, incident resolution)
 * must therefore not be merged into the queued migration, or they are never written. The writer
 * merges by the parameter's class, so the migration must not queue a {@link
 * FlowNodeInstanceDbModel}.
 */
class FlowNodeInstanceMigrationQueueTest {

  private static final long KEY = 1L;
  private static final long TARGET_PROCESS_DEFINITION_KEY = 99L;
  private static final OffsetDateTime END_DATE = OffsetDateTime.now();

  private DefaultExecutionQueue queue;
  private FlowNodeInstanceWriter writer;

  @BeforeEach
  void setUp() {
    // no flush limit: the items stay queued for the test to inspect
    queue =
        new DefaultExecutionQueue(
            mock(SqlSessionFactory.class), 1, 0, new RdbmsWriterMetrics(new SimpleMeterRegistry()));
    writer = new FlowNodeInstanceWriter(queue, mock(FlowNodeInstanceMapper.class));
  }

  @Test
  void shouldQueueCompletionOfAStoredElementSeparatelyFromItsMigration() {
    // when - a stored element is migrated and completed before the next flush
    writer.migrate(migratedElement());
    writer.finish(KEY, FlowNodeState.COMPLETED, END_DATE);

    // then - the completion is written by its own statement
    assertThat(queue.getQueue())
        .extracting(QueueItem::statementId)
        .containsExactly(
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.migrate",
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate");
  }

  @Test
  void shouldMergeCompletionIntoTheQueuedInsertNotIntoTheMigration() {
    // when - an element is activated, migrated and completed before the next flush
    writer.create(activeElement());
    writer.migrate(migratedElement());
    writer.finish(KEY, FlowNodeState.COMPLETED, END_DATE);

    // then - the completion is merged into the insert, which writes state and end date
    assertThat(queue.getQueue()).hasSize(2);
    final var insert = queue.getQueue().getFirst();
    assertThat(insert.statementId())
        .isEqualTo("io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.insert");
    assertThat((FlowNodeInstanceDbModel) insert.parameter())
        .satisfies(
            model -> {
              assertThat(model.state()).isEqualTo(FlowNodeState.COMPLETED);
              assertThat(model.endDate()).isEqualTo(END_DATE);
            });
  }

  @Test
  void shouldQueueIncidentResolutionOfAStoredElementSeparatelyFromItsMigration() {
    // when - a stored element is migrated and its incident resolved before the next flush
    writer.migrate(migratedElement());
    writer.resolveIncident(KEY);

    // then - the resolution is written by its own statement
    assertThat(queue.getQueue())
        .extracting(QueueItem::statementId)
        .containsExactly(
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.migrate",
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateIncident");
  }

  private static FlowNodeInstanceDbModel activeElement() {
    return new FlowNodeInstanceDbModelBuilder()
        .flowNodeInstanceKey(KEY)
        .processInstanceKey(2L)
        .processDefinitionKey(3L)
        .processDefinitionId("process")
        .flowNodeId("task")
        .type(FlowNodeType.SERVICE_TASK)
        .state(FlowNodeState.ACTIVE)
        .startDate(OffsetDateTime.now())
        .incidentKey(42L)
        .build();
  }

  private static FlowNodeInstanceDbModel migratedElement() {
    return activeElement()
        .copy(
            b ->
                ((FlowNodeInstanceDbModelBuilder) b)
                    .processDefinitionKey(TARGET_PROCESS_DEFINITION_KEY));
  }
}
