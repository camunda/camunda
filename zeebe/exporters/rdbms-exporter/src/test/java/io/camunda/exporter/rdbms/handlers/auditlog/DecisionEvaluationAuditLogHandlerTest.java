/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.zeebe.protocol.record.Record;
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

class DecisionEvaluationAuditLogHandlerTest {

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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.decisionDefinitionId()).isEqualTo("decision-1");
    assertThat(entity.decisionDefinitionKey()).isEqualTo(456L);
    assertThat(entity.decisionRequirementsId()).isEqualTo("drg-1");
    assertThat(entity.decisionRequirementsKey()).isEqualTo(789L);
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.decisionEvaluationKey()).isNull();
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.decisionEvaluationKey()).isNull();
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.decisionEvaluationKey()).isEqualTo(999L);
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.decisionEvaluationKey()).isEqualTo(111L);
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
            r -> r.withIntent(DecisionEvaluationIntent.FAILED).withValue(recordValue));

    // when
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.result()).isEqualTo(AuditLogOperationResult.FAIL);
  }
}
