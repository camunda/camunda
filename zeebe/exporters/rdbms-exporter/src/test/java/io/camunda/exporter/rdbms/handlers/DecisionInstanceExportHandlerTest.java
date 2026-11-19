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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
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
  void shouldSetCalledDecisionAsRootOfEveryDecisionInstance() {
    // given - required decisions come first, the called (root) decision last
    final var value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withDecisionKey(300L)
            .withEvaluatedDecisions(
                List.of(
                    evaluatedDecision("d1", 100L),
                    evaluatedDecision("d2", 200L),
                    evaluatedDecision("d3", 300L)))
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

  private EvaluatedDecisionValue evaluatedDecision(
      final String decisionId, final long decisionKey) {
    return ImmutableEvaluatedDecisionValue.builder()
        .from(factory.generateObject(EvaluatedDecisionValue.class))
        .withDecisionId(decisionId)
        .withDecisionKey(decisionKey)
        .withDecisionType("DECISION_TABLE")
        .build();
  }
}
