/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

final class DecisionHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-decision";
  private final DecisionHandler underTest = new DecisionHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionDefinitionEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<DecisionRecordValue> decisionRecord =
        factory.generateRecord(ValueType.DECISION, r -> r.withIntent(ProcessIntent.CREATED));

    // when - then
    assertThat(underTest.handlesRecord(decisionRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final DecisionRecordValue decisionRecordValue =
        ImmutableDecisionRecordValue.builder()
            .from(factory.generateObject(DecisionRecordValue.class))
            .withDecisionKey(expectedId)
            .build();

    final Record<DecisionRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(decisionRecordValue));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final DecisionDefinitionEntity inputEntity = new DecisionDefinitionEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).addWithId(indexName, "0", inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final DecisionRecordValue decisionRecordValue =
        ImmutableDecisionRecordValue.builder()
            .from(factory.generateObject(DecisionRecordValue.class))
            .withDecisionKey(123)
            .withDecisionName("decisionName")
            .withVersion(2)
            .withDecisionId("decisionId")
            .withDecisionRequirementsId("decisionRequirementsId")
            .withDecisionRequirementsKey(222)
            .withTenantId("tenantId")
            .build();

    final Record<DecisionRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(decisionRecordValue));

    // when
    final DecisionDefinitionEntity decisionDefinitionEntity = new DecisionDefinitionEntity();
    underTest.updateEntity(decisionRecord, decisionDefinitionEntity);

    // then
    assertThat(decisionDefinitionEntity.getId()).isEqualTo("123");
    assertThat(decisionDefinitionEntity.getKey()).isEqualTo(123L);
    assertThat(decisionDefinitionEntity.getName()).isEqualTo("decisionName");
    assertThat(decisionDefinitionEntity.getDecisionId()).isEqualTo("decisionId");
    assertThat(decisionDefinitionEntity.getVersion()).isEqualTo(2);
    assertThat(decisionDefinitionEntity.getDecisionRequirementsId())
        .isEqualTo("decisionRequirementsId");
    assertThat(decisionDefinitionEntity.getDecisionRequirementsKey()).isEqualTo(222L);
    assertThat(decisionDefinitionEntity.getTenantId()).isEqualTo("tenantId");
  }
}
