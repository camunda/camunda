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

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionHandlerTest {

  private DecisionHandler underTest;

  @Mock private DecisionIndex mockDecisionIndex;

  @BeforeEach
  public void setup() {
    underTest = new DecisionHandler(mockDecisionIndex);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionDefinitionEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<DecisionRecordValue> mockDecisionRecord = Mockito.mock(Record.class);
    when(mockDecisionRecord.getIntent()).thenReturn(ProcessIntent.CREATED);
    assertThat(underTest.handlesRecord(mockDecisionRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<DecisionRecordValue> mockDecisionRecord = Mockito.mock(Record.class);
    final DecisionRecordValue mockDecisionRecordValue = Mockito.mock(DecisionRecordValue.class);

    when(mockDecisionRecord.getValue()).thenReturn(mockDecisionRecordValue);
    when(mockDecisionRecordValue.getDecisionKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockDecisionRecord);

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
    final String expectedIndexName = "operate-decision";
    when(mockDecisionIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    final DecisionDefinitionEntity inputEntity = new DecisionDefinitionEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).addWithId(expectedIndexName, "0", inputEntity);
    verify(mockDecisionIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-decision";
    when(mockDecisionIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockDecisionIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<DecisionRecordValue> mockDecisionRecord = Mockito.mock(Record.class);
    final DecisionRecordValue mockDecisionRecordValue = Mockito.mock(DecisionRecordValue.class);

    when(mockDecisionRecord.getValue()).thenReturn(mockDecisionRecordValue);
    when(mockDecisionRecordValue.getDecisionKey()).thenReturn(123L);
    when(mockDecisionRecordValue.getDecisionName()).thenReturn("decisionName");
    when(mockDecisionRecordValue.getVersion()).thenReturn(2);
    when(mockDecisionRecordValue.getDecisionId()).thenReturn("decisionId");
    when(mockDecisionRecordValue.getDecisionRequirementsId()).thenReturn("decisionRequirementsId");
    when(mockDecisionRecordValue.getDecisionRequirementsKey()).thenReturn(222L);
    when(mockDecisionRecordValue.getTenantId()).thenReturn("tenantId");

    final DecisionDefinitionEntity decisionDefinitionEntity = new DecisionDefinitionEntity();
    underTest.updateEntity(mockDecisionRecord, decisionDefinitionEntity);

    assertThat(decisionDefinitionEntity.getId()).isEqualTo("123");
    assertThat(decisionDefinitionEntity.getKey()).isEqualTo(123L);
    assertThat(decisionDefinitionEntity.getName()).isEqualTo("decisionName");
    assertThat(decisionDefinitionEntity.getVersion()).isEqualTo(2);
    assertThat(decisionDefinitionEntity.getDecisionRequirementsId())
        .isEqualTo("decisionRequirementsId");
    assertThat(decisionDefinitionEntity.getDecisionRequirementsKey()).isEqualTo(222L);
    assertThat(decisionDefinitionEntity.getTenantId()).isEqualTo("tenantId");
  }
}
