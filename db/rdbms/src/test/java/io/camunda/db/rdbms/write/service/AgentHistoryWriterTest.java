/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AgentHistoryMapper;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AgentHistoryWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AgentHistoryMapper mapper = mock(AgentHistoryMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final AgentHistoryWriter writer =
      new AgentHistoryWriter(executionQueue, mapper, vendorDatabaseProperties);

  AgentHistoryWriterTest() {
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(Integer.MAX_VALUE);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(null);
  }

  @Test
  void shouldQueueInsertOnCreate() {
    // given
    final var model = buildModel(1L, "myLease");

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_HISTORY,
                    WriteStatementType.INSERT,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentHistoryMapper.insert",
                    model)));
  }

  @Test
  void shouldQueueUpdateOnUpdateCommitStatus() {
    // given
    final var model = buildModel(2L, "otherLease");

    // when
    writer.updateCommitStatus(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_HISTORY,
                    WriteStatementType.UPDATE,
                    2L,
                    "io.camunda.db.rdbms.sql.AgentHistoryMapper.updateCommitStatus",
                    model)));
  }

  @Test
  void shouldTruncateJobLeaseOnCreate() {
    // given — column size of 10; value longer than 10 chars
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(10);
    final String longLease = "a".repeat(20);
    final var model = buildModel(3L, longLease);

    // when
    writer.create(model);

    // then — jobLease is truncated in-place to the column size limit
    assertThat(model.jobLease()).hasSize(10);
    assertThat(longLease).startsWith(model.jobLease());
  }

  private AgentHistoryDbModel buildModel(final long key, final String jobLease) {
    return new AgentHistoryDbModel.Builder()
        .agentHistoryKey(key)
        .agentInstanceKey(100L)
        .elementInstanceKey(200L)
        .processInstanceKey(300L)
        .rootProcessInstanceKey(400L)
        .processDefinitionId("myProcess")
        .processDefinitionKey(500L)
        .tenantId("myTenant")
        .partitionId(1)
        .jobKey(600L)
        .jobLease(jobLease)
        .role(AgentInstanceHistoryRole.USER)
        .commitStatus(AgentInstanceHistoryCommitStatus.PENDING)
        .producedAt(OffsetDateTime.now())
        .build();
  }
}
