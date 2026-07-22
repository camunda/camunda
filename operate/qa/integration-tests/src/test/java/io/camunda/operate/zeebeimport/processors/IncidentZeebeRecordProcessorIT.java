/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.util.ZeebeRecordTestUtil.createIncidentZeebeRecord;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class IncidentZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;
  @Autowired private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldImportExecutionListenerNoRetriesIncident()
      throws PersistenceException, IOException {
    // given
    final long incidentKey = 1L;

    final Record<IncidentRecordValue> zeebeRecord =
        createIncidentZeebeRecord(
            b -> b.withIntent(CREATED).withKey(incidentKey),
            b -> b.withErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES).withErrorMessage("foo"));

    // when
    importIncidentZeebeRecord(zeebeRecord);

    // then
    final IncidentEntity incidentEntity = findIncidentByKey(incidentKey);

    // the error type was imported correctly
    assertThat(incidentEntity.getErrorType())
        .isEqualTo(io.camunda.operate.entities.ErrorType.EXECUTION_LISTENER_NO_RETRIES);
  }

  /**
   * Regression test for https://github.com/camunda/camunda/issues/56117. The processor must write
   * every post-importer-queue entry with {@code routing == partitionId} so that a partition's
   * entries co-locate on a single shard; without this the entries scatter across shards that
   * refresh independently and the strict, forward-only position cursor of the incident post-import
   * can skip a lower-position entry, leaving its incident stuck {@code PENDING} forever. This is
   * asserted end-to-end against the real store (Elasticsearch and OpenSearch): entries are queried
   * back by the {@code _routing} metadata field, which only matches documents that were actually
   * indexed with that routing value.
   */
  @Test
  public void shouldRoutePostImporterQueueEntriesByPartitionId()
      throws PersistenceException, IOException {
    // given - CREATED incidents on two different partitions
    final int partitionA = 3;
    final int partitionB = 5;
    final List<Record<IncidentRecordValue>> records =
        List.of(
            createdIncidentOnPartition(31L, partitionA, 1L),
            createdIncidentOnPartition(32L, partitionA, 2L),
            createdIncidentOnPartition(33L, partitionA, 3L),
            createdIncidentOnPartition(51L, partitionB, 1L));

    // when
    importIncidentZeebeRecords(records);

    // then - a partition's entries are all queryable by _routing == partitionId (proving they were
    // indexed with that routing) and by nothing else
    assertThat(findQueueEntriesByRouting(partitionA))
        .hasSize(3)
        .allSatisfy(entry -> assertThat(entry.getPartitionId()).isEqualTo(partitionA))
        .extracting(PostImporterQueueEntity::getKey)
        .containsExactlyInAnyOrder(31L, 32L, 33L);
    assertThat(findQueueEntriesByRouting(partitionB))
        .hasSize(1)
        .allSatisfy(entry -> assertThat(entry.getPartitionId()).isEqualTo(partitionB))
        .extracting(PostImporterQueueEntity::getKey)
        .containsExactly(51L);
  }

  private List<PostImporterQueueEntity> findQueueEntriesByRouting(final int partitionId)
      throws IOException {
    return testSearchRepository.searchTerm(
        postImporterQueueTemplate.getFullQualifiedName(),
        "_routing",
        String.valueOf(partitionId),
        PostImporterQueueEntity.class,
        10);
  }

  @NotNull
  private Record<IncidentRecordValue> createdIncidentOnPartition(
      final long key, final int partitionId, final long position) {
    return createIncidentZeebeRecord(
        b -> b.withIntent(CREATED).withKey(key).withPartitionId(partitionId).withPosition(position),
        b -> b.withErrorType(ErrorType.UNKNOWN).withErrorMessage("boom"));
  }

  @NotNull
  private IncidentEntity findIncidentByKey(final long key) throws IOException {
    final List<IncidentEntity> entities =
        testSearchRepository.searchTerm(
            incidentTemplate.getFullQualifiedName(), "key", key, IncidentEntity.class, 10);
    final Optional<IncidentEntity> first = entities.stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    incidentZeebeRecordProcessor.processIncidentRecord(List.of(zeebeRecord), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(incidentTemplate.getFullQualifiedName());
  }

  private void importIncidentZeebeRecords(final List<Record<IncidentRecordValue>> zeebeRecords)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    for (final Record<IncidentRecordValue> zeebeRecord : zeebeRecords) {
      incidentZeebeRecordProcessor.processIncidentRecord(zeebeRecord, batchRequest, incident -> {});
    }
    batchRequest.execute();
    searchContainerManager.refreshIndices(incidentTemplate.getFullQualifiedName());
    searchContainerManager.refreshIndices(postImporterQueueTemplate.getFullQualifiedName());
  }
}
