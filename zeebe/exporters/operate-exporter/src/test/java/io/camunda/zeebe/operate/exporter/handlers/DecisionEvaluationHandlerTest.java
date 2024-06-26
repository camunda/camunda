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
import static org.mockito.Mockito.times;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionEvaluationHandlerTest {

  private DecisionEvaluationHandler underTest;

  @Mock private DecisionInstanceTemplate mockDecisionInstanceTemplate;

  @BeforeEach
  public void setup() {
    underTest = new DecisionEvaluationHandler(mockDecisionInstanceTemplate);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION_EVALUATION);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionInstanceEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<DecisionEvaluationRecordValue> mockDecisionEvaluationRecord =
        Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockDecisionEvaluationRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<DecisionEvaluationRecordValue> mockDecisionEvaluationRecord =
        Mockito.mock(Record.class);
    final DecisionEvaluationRecordValue mockDecisionEvaluationRecordValue =
        Mockito.mock(DecisionEvaluationRecordValue.class);
    final EvaluatedDecisionValue mockEvaluatedDecisionValue =
        Mockito.mock(EvaluatedDecisionValue.class);

    when(mockDecisionEvaluationRecord.getValue()).thenReturn(mockDecisionEvaluationRecordValue);
    when(mockDecisionEvaluationRecordValue.getEvaluatedDecisions())
        .thenReturn(List.of(mockEvaluatedDecisionValue));
    when(mockDecisionEvaluationRecord.getKey()).thenReturn(123L);

    final String expectedId = new DecisionInstanceEntity().setId(123L, 1).getId();

    final var idList = underTest.generateIds(mockDecisionEvaluationRecord);

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
    final String expectedIndexName = "operate-decision-instance";
    when(mockDecisionInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final DecisionInstanceEntity inputEntity = new DecisionInstanceEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).add(expectedIndexName, inputEntity);
    verify(mockDecisionInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-decision-instance";
    when(mockDecisionInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockDecisionInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<DecisionEvaluationRecordValue> mockDecisionEvaluationRecord =
        Mockito.mock(Record.class);
    final DecisionEvaluationRecordValue mockDecisionEvaluationRecordValue =
        Mockito.mock(DecisionEvaluationRecordValue.class);
    final EvaluatedDecisionValue mockEvaluatedDecisionValue =
        Mockito.mock(EvaluatedDecisionValue.class);

    when(mockDecisionEvaluationRecord.getValue()).thenReturn(mockDecisionEvaluationRecordValue);
    when(mockDecisionEvaluationRecord.getKey()).thenReturn(222L);
    when(mockDecisionEvaluationRecord.getPartitionId()).thenReturn(1);
    when(mockDecisionEvaluationRecord.getPosition()).thenReturn(10L);
    when(mockDecisionEvaluationRecord.getTimestamp()).thenReturn(1000L);
    when(mockDecisionEvaluationRecord.getIntent()).thenReturn(DecisionEvaluationIntent.FAILED);
    when(mockDecisionEvaluationRecordValue.getEvaluatedDecisions())
        .thenReturn(List.of(mockEvaluatedDecisionValue));
    when(mockDecisionEvaluationRecordValue.getProcessInstanceKey()).thenReturn(333L);
    when(mockDecisionEvaluationRecordValue.getProcessDefinitionKey()).thenReturn(444L);
    when(mockDecisionEvaluationRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockDecisionEvaluationRecordValue.getElementInstanceKey()).thenReturn(678L);
    when(mockDecisionEvaluationRecordValue.getElementId()).thenReturn("elementId");
    when(mockDecisionEvaluationRecordValue.getDecisionRequirementsKey()).thenReturn(111L);
    when(mockDecisionEvaluationRecordValue.getDecisionRequirementsId())
        .thenReturn("decisionRequirementsId");
    when(mockDecisionEvaluationRecordValue.getDecisionId()).thenReturn("rootDecisionId");
    when(mockDecisionEvaluationRecordValue.getDecisionName()).thenReturn("rootDecisionName");
    when(mockDecisionEvaluationRecordValue.getDecisionKey()).thenReturn(1234L);
    when(mockDecisionEvaluationRecordValue.getTenantId()).thenReturn("tenantId");
    when(mockDecisionEvaluationRecordValue.getEvaluationFailureMessage())
        .thenReturn("errorMessage");
    when(mockEvaluatedDecisionValue.getDecisionId()).thenReturn("decisionId");
    when(mockEvaluatedDecisionValue.getDecisionKey()).thenReturn(777L);
    when(mockEvaluatedDecisionValue.getDecisionType()).thenReturn("LITERAL_EXPRESSION");
    when(mockEvaluatedDecisionValue.getDecisionName()).thenReturn("decisionName");
    when(mockEvaluatedDecisionValue.getDecisionVersion()).thenReturn(3);
    when(mockEvaluatedDecisionValue.getDecisionOutput()).thenReturn("decisionOutput");
    when(mockEvaluatedDecisionValue.getMatchedRules()).thenReturn(List.of());
    when(mockEvaluatedDecisionValue.getEvaluatedInputs()).thenReturn(List.of());

    final DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(222L, 1);
    underTest.updateEntity(mockDecisionEvaluationRecord, decisionInstanceEntity);

    assertThat(decisionInstanceEntity.getKey()).isEqualTo(222L);
    assertThat(decisionInstanceEntity.getExecutionIndex()).isEqualTo(1);
    assertThat(decisionInstanceEntity.getPartitionId()).isEqualTo(1);
    assertThat(decisionInstanceEntity.getPosition()).isEqualTo(10L);
    assertThat(decisionInstanceEntity.getEvaluationDate())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(1000L)));
    assertThat(decisionInstanceEntity.getProcessInstanceKey()).isEqualTo(333L);
    assertThat(decisionInstanceEntity.getProcessDefinitionKey()).isEqualTo(444L);
    assertThat(decisionInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(decisionInstanceEntity.getElementInstanceKey()).isEqualTo(678L);
    assertThat(decisionInstanceEntity.getElementId()).isEqualTo("elementId");
    assertThat(decisionInstanceEntity.getDecisionRequirementsKey()).isEqualTo(111L);
    assertThat(decisionInstanceEntity.getDecisionRequirementsId())
        .isEqualTo("decisionRequirementsId");
    assertThat(decisionInstanceEntity.getRootDecisionId()).isEqualTo("rootDecisionId");
    assertThat(decisionInstanceEntity.getRootDecisionName()).isEqualTo("rootDecisionName");
    assertThat(decisionInstanceEntity.getRootDecisionDefinitionId()).isEqualTo("1234");
    assertThat(decisionInstanceEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(decisionInstanceEntity.getEvaluationFailure()).isEqualTo("errorMessage");
    assertThat(decisionInstanceEntity.getDecisionId()).isEqualTo("decisionId");
    assertThat(decisionInstanceEntity.getDecisionDefinitionId()).isEqualTo("777");
    assertThat(decisionInstanceEntity.getDecisionType()).isEqualTo(DecisionType.LITERAL_EXPRESSION);
    assertThat(decisionInstanceEntity.getDecisionName()).isEqualTo("decisionName");
    assertThat(decisionInstanceEntity.getDecisionVersion()).isEqualTo(3);
    assertThat(decisionInstanceEntity.getState()).isEqualTo(DecisionInstanceState.FAILED);
    assertThat(decisionInstanceEntity.getResult()).isEqualTo("decisionOutput");
    assertThat(decisionInstanceEntity.getEvaluatedOutputs()).isEqualTo(List.of());
    assertThat(decisionInstanceEntity.getEvaluatedInputs()).isEqualTo(List.of());
  }
}
