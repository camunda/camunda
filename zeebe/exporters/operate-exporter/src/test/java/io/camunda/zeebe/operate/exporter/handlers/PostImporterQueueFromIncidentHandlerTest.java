/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PostImporterQueueFromIncidentHandlerTest {

  private PostImporterQueueFromIncidentHandler underTest;

  @Mock private PostImporterQueueTemplate mockPostImporterQueueTemplate;

  @BeforeEach
  public void setup() {
    underTest = new PostImporterQueueFromIncidentHandler(mockPostImporterQueueTemplate);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(PostImporterQueueEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    when(mockIncidentRecord.getKey()).thenReturn(123L);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);

    final String expectedId = "123-CREATED";
    final var idList = underTest.generateIds(mockIncidentRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly(expectedId);
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "post-importer-queue";
    when(mockPostImporterQueueTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final PostImporterQueueEntity inputEntity = new PostImporterQueueEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).add(expectedIndexName, inputEntity);
    verify(mockPostImporterQueueTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "post-importer-queue";
    when(mockPostImporterQueueTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockPostImporterQueueTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecord.getKey()).thenReturn(25L);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    when(mockIncidentRecord.getPartitionId()).thenReturn(10);
    when(mockIncidentRecord.getPosition()).thenReturn(65L);
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(123L);

    final PostImporterQueueEntity postImporterQueueEntity = new PostImporterQueueEntity();
    underTest.updateEntity(mockIncidentRecord, postImporterQueueEntity);

    assertThat(postImporterQueueEntity.getId()).isEqualTo("25-CREATED");
    assertThat(postImporterQueueEntity.getKey()).isEqualTo(25L);
    assertThat(postImporterQueueEntity.getIntent()).isEqualTo("CREATED");
    assertThat(postImporterQueueEntity.getPartitionId()).isEqualTo(10);
    assertThat(postImporterQueueEntity.getPosition()).isEqualTo(65L);
    assertThat(postImporterQueueEntity.getProcessInstanceKey()).isEqualTo(123L);
  }
}
