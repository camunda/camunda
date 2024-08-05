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

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionRequirementsHandlerTest {

  private DecisionRequirementsHandler underTest;

  @Mock private DecisionRequirementsIndex mockDecisionRequirementsIndex;

  @BeforeEach
  public void setup() {
    underTest = new DecisionRequirementsHandler(mockDecisionRequirementsIndex);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION_REQUIREMENTS);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionRequirementsEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<DecisionRequirementsRecordValue> mockDecisionRequirementsRecord =
        Mockito.mock(Record.class);
    when(mockDecisionRequirementsRecord.getIntent()).thenReturn(ProcessIntent.CREATED);
    assertThat(underTest.handlesRecord(mockDecisionRequirementsRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<DecisionRequirementsRecordValue> mockDecisionRequirementsRecord =
        Mockito.mock(Record.class);
    final DecisionRequirementsRecordValue mockDecisionRequirementsRecordValue =
        Mockito.mock(DecisionRequirementsRecordValue.class);

    when(mockDecisionRequirementsRecord.getValue()).thenReturn(mockDecisionRequirementsRecordValue);
    when(mockDecisionRequirementsRecordValue.getDecisionRequirementsKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockDecisionRequirementsRecord);

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
    final String expectedIndexName = "operate-decision-requirements";
    when(mockDecisionRequirementsIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    final DecisionRequirementsEntity inputEntity = new DecisionRequirementsEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).addWithId(expectedIndexName, "0", inputEntity);
    verify(mockDecisionRequirementsIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-decision-requirements";
    when(mockDecisionRequirementsIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockDecisionRequirementsIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<DecisionRequirementsRecordValue> mockDecisionRequirementsRecord =
        Mockito.mock(Record.class);
    final DecisionRequirementsRecordValue mockDecisionRequirementsRecordValue =
        Mockito.mock(DecisionRequirementsRecordValue.class);

    when(mockDecisionRequirementsRecord.getValue()).thenReturn(mockDecisionRequirementsRecordValue);
    when(mockDecisionRequirementsRecordValue.getDecisionRequirementsKey()).thenReturn(123L);
    when(mockDecisionRequirementsRecordValue.getDecisionRequirementsName())
        .thenReturn("decisionRequirementsName");
    when(mockDecisionRequirementsRecordValue.getDecisionRequirementsId())
        .thenReturn("decisionRequirementsId");
    when(mockDecisionRequirementsRecordValue.getDecisionRequirementsVersion()).thenReturn(2);
    when(mockDecisionRequirementsRecordValue.getResourceName()).thenReturn("resourceName");
    when(mockDecisionRequirementsRecordValue.getResource()).thenReturn(new byte[] {1, 2, 3});
    when(mockDecisionRequirementsRecordValue.getTenantId()).thenReturn("tenantId");

    final DecisionRequirementsEntity decisionRequirementsEntity = new DecisionRequirementsEntity();
    underTest.updateEntity(mockDecisionRequirementsRecord, decisionRequirementsEntity);

    assertThat(decisionRequirementsEntity.getId()).isEqualTo("123");
    assertThat(decisionRequirementsEntity.getKey()).isEqualTo(123L);
    assertThat(decisionRequirementsEntity.getName()).isEqualTo("decisionRequirementsName");
    assertThat(decisionRequirementsEntity.getDecisionRequirementsId())
        .isEqualTo("decisionRequirementsId");
    assertThat(decisionRequirementsEntity.getVersion()).isEqualTo(2);
    assertThat(decisionRequirementsEntity.getResourceName()).isEqualTo("resourceName");
    assertThat(decisionRequirementsEntity.getXml())
        .isEqualTo(new String(new byte[] {1, 2, 3}, StandardCharsets.UTF_8));
    assertThat(decisionRequirementsEntity.getTenantId()).isEqualTo("tenantId");
  }
}
