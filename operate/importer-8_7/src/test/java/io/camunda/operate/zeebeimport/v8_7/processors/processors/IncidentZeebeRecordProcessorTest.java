/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.zeebeimport.IncidentNotifier;
import io.camunda.operate.zeebeimport.processors.IncidentZeebeRecordProcessor;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentZeebeRecordProcessorTest {

  private static final String INCIDENT_INDEX = "incident-index";
  private static final String POST_IMPORTER_QUEUE_INDEX = "post-importer-queue-index";

  @Mock private OperateProperties operateProperties;
  @Mock private IncidentTemplate incidentTemplate;
  @Mock private PostImporterQueueTemplate postImporterQueueTemplate;
  @Mock private OperationsManager operationsManager;
  @Mock private IncidentNotifier incidentNotifier;
  @InjectMocks private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;

  @Test
  void shouldRoutePostImporterQueueEntryByPartitionId() throws PersistenceException {
    // given - an incident record on a specific partition
    final long incidentKey = 42L;
    final int partitionId = 3;
    final long position = 7L;
    final BatchRequest batchRequest = mock(BatchRequest.class);
    when(incidentTemplate.getFullQualifiedName()).thenReturn(INCIDENT_INDEX);
    when(postImporterQueueTemplate.getFullQualifiedName()).thenReturn(POST_IMPORTER_QUEUE_INDEX);

    final Record<IncidentRecordValue> record =
        incidentRecord(IncidentIntent.CREATED, incidentKey, partitionId, position);

    // when
    incidentZeebeRecordProcessor.processIncidentRecord(record, batchRequest, incident -> {});

    // then - the post-importer-queue entry is written with routing == partition id (see #56117)
    // so that a partition's entries co-locate on a single shard and cannot exhibit refresh skew
    final ArgumentCaptor<PostImporterQueueEntity> captor =
        ArgumentCaptor.forClass(PostImporterQueueEntity.class);
    verify(batchRequest)
        .addWithRouting(
            eq(POST_IMPORTER_QUEUE_INDEX), captor.capture(), eq(String.valueOf(partitionId)));
    // ...and never with an unrouted add, which was the source of the bug
    verify(batchRequest, never()).add(eq(POST_IMPORTER_QUEUE_INDEX), any());

    final PostImporterQueueEntity queueEntity = captor.getValue();
    assertThat(queueEntity.getKey()).isEqualTo(incidentKey);
    assertThat(queueEntity.getPartitionId()).isEqualTo(partitionId);
    assertThat(queueEntity.getPosition()).isEqualTo(position);
    assertThat(queueEntity.getIntent()).isEqualTo(IncidentIntent.CREATED.name());
  }

  private Record<IncidentRecordValue> incidentRecord(
      final IncidentIntent intent, final long key, final int partitionId, final long position) {
    final IncidentRecordValue recordValue =
        ImmutableIncidentRecordValue.builder()
            .withProcessInstanceKey(key)
            .withErrorType(ErrorType.UNKNOWN)
            .withErrorMessage("boom")
            .build();
    return ImmutableRecord.<IncidentRecordValue>builder()
        .withKey(key)
        .withPartitionId(partitionId)
        .withPosition(position)
        .withIntent(intent)
        .withTimestamp(1000L)
        .withValueType(ValueType.INCIDENT)
        .withValue(recordValue)
        .build();
  }
}
