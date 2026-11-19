/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionInstanceExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  @Mock private DecisionInstanceWriter decisionInstanceWriter;
  @Captor private ArgumentCaptor<DecisionInstanceDbModel> dbModelCaptor;
  private DecisionInstanceExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DecisionInstanceExportHandler(decisionInstanceWriter);
  }

  @Test
  void shouldExportEveryDecisionReadingEvaluatedDecisionsOnce() {
    // given - getEvaluatedDecisions() copies every decision on each call in the broker, so
    // calling it per decision makes the export cost quadratic in the DRG size
    final List<EvaluatedDecisionValue> decisions =
        List.of(evaluatedDecision("d1"), evaluatedDecision("d2"), evaluatedDecision("d3"));
    final DecisionEvaluationRecordValue value =
        mock(
            DecisionEvaluationRecordValue.class,
            delegatesTo(
                ImmutableDecisionEvaluationRecordValue.builder()
                    .from(factory.generateObject(DecisionEvaluationRecordValue.class))
                    .withEvaluatedDecisions(decisions)
                    .build()));
    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(decisionInstanceWriter, times(3)).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getAllValues())
        .extracting(DecisionInstanceDbModel::decisionDefinitionId)
        .containsExactly("d1", "d2", "d3");
    verify(value, times(1)).getEvaluatedDecisions();
  }

  @Test
  void shouldSetCalledDecisionAsRootOfEveryDecisionInstance() {
    // given - required decisions come first, the called (root) decision last
    final var value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionKey(300L)
            .withEvaluatedDecisions(
                List.of(
                    ImmutableEvaluatedDecisionValue.builder()
                        .from(evaluatedDecision("d1"))
                        .withDecisionKey(100L)
                        .build(),
                    ImmutableEvaluatedDecisionValue.builder()
                        .from(evaluatedDecision("d2"))
                        .withDecisionKey(200L)
                        .build(),
                    ImmutableEvaluatedDecisionValue.builder()
                        .from(evaluatedDecision("d3"))
                        .withDecisionKey(300L)
                        .build()))
            .build();
    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(decisionInstanceWriter, times(3)).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getAllValues())
        .extracting(
            DecisionInstanceDbModel::decisionDefinitionKey,
            DecisionInstanceDbModel::rootDecisionDefinitionKey)
        .containsExactly(tuple(100L, 300L), tuple(200L, 300L), tuple(300L, 300L));
  }

  @Test
  void shouldExportFailedEvaluationWithFailureMessageOnLastDecision() {
    // given - the failing decision is the last one of a FAILED record
    final var value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluationFailureMessage("boom")
            .withEvaluatedDecisions(List.of(evaluatedDecision("d1"), evaluatedDecision("d2")))
            .build();
    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.FAILED).withValue(value));

    // when
    assertThat(handler.canExport(record)).isTrue();
    handler.export(record);

    // then
    verify(decisionInstanceWriter, times(2)).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getAllValues())
        .extracting(
            DecisionInstanceDbModel::state, DecisionInstanceDbModel::evaluationFailureMessage)
        .containsExactly(
            tuple(DecisionInstanceState.EVALUATED, null),
            tuple(DecisionInstanceState.FAILED, "boom"));
  }

  @Test
  void shouldNotExportOtherIntents() {
    // given
    final Record<DecisionEvaluationRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION, r -> r.withIntent(DecisionEvaluationIntent.EVALUATE));

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  private EvaluatedDecisionValue evaluatedDecision(final String decisionId) {
    return ImmutableEvaluatedDecisionValue.builder()
        .from(factory.generateObject(EvaluatedDecisionValue.class))
        .withDecisionId(decisionId)
        .withDecisionType("DECISION_TABLE")
        .build();
  }
}
