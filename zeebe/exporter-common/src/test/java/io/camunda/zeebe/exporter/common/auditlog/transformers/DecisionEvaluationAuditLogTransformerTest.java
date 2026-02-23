/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DecisionEvaluationAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final DecisionEvaluationAuditLogTransformer transformer =
      new DecisionEvaluationAuditLogTransformer();

  @Test
  void shouldTransformDecisionEvaluationRecord() {
    // given
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .withDecisionRequirementsId("drg-1")
            .withDecisionRequirementsKey(789L)
            .withTenantId("tenant-1")
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getDecisionDefinitionId()).isEqualTo("decision-1");
    assertThat(entity.getDecisionDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getDecisionRequirementsId()).isEqualTo("drg-1");
    assertThat(entity.getDecisionRequirementsKey()).isEqualTo(789L);
    assertThat(entity.getTenant().get().tenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldHandleNullEvaluatedDecisions() {
    // given
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getDecisionEvaluationKey()).isNull();
  }

  @Test
  void shouldHandleEmptyEvaluatedDecisions() {
    // given
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .withEvaluatedDecisions(Collections.emptyList())
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getDecisionEvaluationKey()).isNull();
  }

  @Test
  void shouldExtractDecisionEvaluationKeyFromSingleEvaluatedDecision() {
    // given
    final ImmutableEvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionKey(999L)
            .build();

    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .withEvaluatedDecisions(List.of(evaluatedDecision))
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getDecisionEvaluationKey()).isEqualTo(999L);
  }

  @Test
  void shouldExtractDecisionEvaluationKeyFromFirstOfMultipleEvaluatedDecisions() {
    // given
    final ImmutableEvaluatedDecisionValue firstDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionKey(111L)
            .build();

    final ImmutableEvaluatedDecisionValue secondDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionKey(222L)
            .build();

    final ImmutableEvaluatedDecisionValue thirdDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionKey(333L)
            .build();

    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .withEvaluatedDecisions(List.of(firstDecision, secondDecision, thirdDecision))
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getDecisionEvaluationKey()).isEqualTo(111L);
  }

  @Test
  void shouldSetResultToFailWhenIntentIsFailed() {
    // given
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionId("decision-1")
            .withDecisionKey(456L)
            .build();

    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.FAILED)
                    .withRejectionType(RejectionType.INVALID_STATE)
                    .withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getResult())
        .isEqualTo(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.FAIL);
    assertThat(entity.getEntityDescription()).isEqualTo(RejectionType.INVALID_STATE.name());
  }

  @ParameterizedTest
  @EnumSource(
      value = DecisionEvaluationIntent.class,
      names = {"EVALUATED", "FAILED"})
  void shouldScheduleCleanUp(final DecisionEvaluationIntent intent) {
    // given
    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(ValueType.DECISION_EVALUATION, r -> r.withIntent(intent));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
