/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
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
  void shouldSetBusinessId() {
    // given
    final var record = decisionEvaluationRecord("order-123");

    // when
    handler.export(record);

    // then
    verify(decisionInstanceWriter).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().businessId()).isEqualTo("order-123");
  }

  @Test
  void shouldNotSetBusinessIdWhenEmpty() {
    // given - the owning instance has no business ID, stored as an empty string on the record
    final var record = decisionEvaluationRecord("");

    // when
    handler.export(record);

    // then - the empty string is normalised to null, matching the ES/OS behaviour
    verify(decisionInstanceWriter).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().businessId()).isNull();
  }

  private Record<DecisionEvaluationRecordValue> decisionEvaluationRecord(final String businessId) {
    final EvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionType("DECISION_TABLE")
            .build();
    final DecisionEvaluationRecordValue value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(List.of(evaluatedDecision))
            .withBusinessId(businessId)
            .build();
    return factory.generateRecord(
        ValueType.DECISION_EVALUATION,
        r -> r.withIntent(DecisionEvaluationIntent.EVALUATED).withValue(value));
  }
}
