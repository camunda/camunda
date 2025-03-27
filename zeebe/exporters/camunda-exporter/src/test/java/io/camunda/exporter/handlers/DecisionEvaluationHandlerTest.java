/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.ImmutableMatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class DecisionEvaluationHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-decision-evaluation";
  private final DecisionEvaluationHandler underTest = new DecisionEvaluationHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION_EVALUATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionInstanceEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION, r -> r.withIntent(DecisionEvaluationIntent.EVALUATED));

    // when - then
    assertThat(underTest.handlesRecord(decisionRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final ImmutableEvaluatedDecisionValue decisionValue =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .build();
    final long expectedId = 123;
    final DecisionEvaluationRecordValue decisionRecordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(List.of(decisionValue))
            .build();

    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionRecordValue)
                    .withKey(123L));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(expectedId + "-" + 1);
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
    final DecisionInstanceEntity inputEntity = new DecisionInstanceEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @ParameterizedTest
  @EnumSource(
      value = DecisionType.class,
      names = {"DECISION_TABLE", "LITERAL_EXPRESSION"})
  void shouldUpdateEntityFromRecord(final DecisionType decisionType) {
    // given
    final long recordKey = 123L;
    final ImmutableEvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionType(decisionType.name())
            .withMatchedRules(
                List.of(
                    ImmutableMatchedRuleValue.builder()
                        .from(factory.generateObject(MatchedRuleValue.class))
                        .withEvaluatedOutputs(
                            List.of(factory.generateObject(ImmutableEvaluatedOutputValue.class)))
                        .build()))
            .withEvaluatedInputs(
                List.of(factory.generateObject(ImmutableEvaluatedInputValue.class)))
            .build();

    final DecisionEvaluationRecordValue decisionRecordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(List.of(evaluatedDecision))
            .build();

    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionRecordValue)
                    .withKey(recordKey));

    // when
    final DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    // then
    assertThat(decisionInstanceEntity.getId()).isEqualTo(recordKey + "-1");
    assertThat(decisionInstanceEntity.getKey()).isEqualTo(recordKey);
    assertThat(decisionInstanceEntity.getExecutionIndex()).isEqualTo(1);
    assertThat(decisionInstanceEntity.getEvaluationDate())
        .isEqualTo(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(decisionRecord.getTimestamp()), ZoneOffset.UTC));
    assertThat(decisionInstanceEntity.getProcessInstanceKey())
        .isEqualTo(decisionRecordValue.getProcessInstanceKey());
    assertThat(decisionInstanceEntity.getProcessDefinitionKey())
        .isEqualTo(decisionRecordValue.getProcessDefinitionKey());
    assertThat(decisionInstanceEntity.getBpmnProcessId())
        .isEqualTo(decisionRecordValue.getBpmnProcessId());
    assertThat(decisionInstanceEntity.getElementInstanceKey())
        .isEqualTo(decisionRecordValue.getElementInstanceKey());
    assertThat(decisionInstanceEntity.getElementId()).isEqualTo(decisionRecordValue.getElementId());
    assertThat(decisionInstanceEntity.getDecisionRequirementsKey())
        .isEqualTo(decisionRecordValue.getDecisionRequirementsKey());
    assertThat(decisionInstanceEntity.getDecisionRequirementsId())
        .isEqualTo(decisionRecordValue.getDecisionRequirementsId());
    assertThat(decisionInstanceEntity.getRootDecisionId())
        .isEqualTo(decisionRecordValue.getDecisionId());
    assertThat(decisionInstanceEntity.getRootDecisionName())
        .isEqualTo(decisionRecordValue.getDecisionName());
    assertThat(decisionInstanceEntity.getRootDecisionDefinitionId())
        .isEqualTo(String.valueOf(decisionRecordValue.getDecisionKey()));
    assertThat(decisionInstanceEntity.getDecisionId()).isEqualTo(evaluatedDecision.getDecisionId());
    assertThat(decisionInstanceEntity.getDecisionDefinitionId())
        .isEqualTo(String.valueOf(evaluatedDecision.getDecisionKey()));
    assertThat(decisionInstanceEntity.getDecisionType())
        .isEqualTo(DecisionType.fromZeebeDecisionType(decisionType.name()));
    assertThat(decisionInstanceEntity.getDecisionName())
        .isEqualTo(evaluatedDecision.getDecisionName());
    assertThat(decisionInstanceEntity.getDecisionVersion())
        .isEqualTo(evaluatedDecision.getDecisionVersion());
    assertThat(decisionInstanceEntity.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(decisionInstanceEntity.getResult()).isEqualTo(evaluatedDecision.getDecisionOutput());

    assertThat(decisionInstanceEntity.getEvaluatedOutputs()).isNotEmpty();
    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getRuleId())
        .isEqualTo(evaluatedDecision.getMatchedRules().getFirst().getRuleId());
    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getRuleIndex())
        .isEqualTo(evaluatedDecision.getMatchedRules().getFirst().getRuleIndex());
    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getId())
        .isEqualTo(
            evaluatedDecision
                .getMatchedRules()
                .getFirst()
                .getEvaluatedOutputs()
                .getFirst()
                .getOutputId());

    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getName())
        .isEqualTo(
            evaluatedDecision
                .getMatchedRules()
                .getFirst()
                .getEvaluatedOutputs()
                .getFirst()
                .getOutputName());

    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getValue())
        .isEqualTo(
            evaluatedDecision
                .getMatchedRules()
                .getFirst()
                .getEvaluatedOutputs()
                .getFirst()
                .getOutputValue());

    assertThat(decisionInstanceEntity.getEvaluatedInputs()).isNotEmpty();
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getId())
        .isEqualTo(evaluatedDecision.getEvaluatedInputs().getFirst().getInputId());
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getName())
        .isEqualTo(evaluatedDecision.getEvaluatedInputs().getFirst().getInputName());
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getValue())
        .isEqualTo(evaluatedDecision.getEvaluatedInputs().getFirst().getInputValue());

    assertThat(decisionInstanceEntity.getPosition()).isEqualTo(decisionRecord.getPosition());
    assertThat(decisionInstanceEntity.getPartitionId()).isEqualTo(decisionRecord.getPartitionId());
    assertThat(decisionInstanceEntity.getTenantId()).isEqualTo(decisionRecordValue.getTenantId());
  }

  @Test
  void shouldUpdateEntityFromRecordWithStateFailed() {
    // given
    final long recordKey = 123L;
    final ImmutableEvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionType(DecisionType.DECISION_TABLE.name())
            .build();

    final DecisionEvaluationRecordValue decisionRecordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(List.of(evaluatedDecision))
            .build();

    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.FAILED)
                    .withValue(decisionRecordValue)
                    .withKey(recordKey));

    // when
    final DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    // then
    assertThat(decisionInstanceEntity.getId()).isEqualTo(recordKey + "-1");
    assertThat(decisionInstanceEntity.getKey()).isEqualTo(recordKey);
    assertThat(decisionInstanceEntity.getState()).isEqualTo(DecisionInstanceState.FAILED);
  }

  @Test
  void shouldUpdateWithMultipleDecisionsOnCorrectState() {
    // given
    final long recordKey = 123L;
    final DecisionEvaluationRecordValue decisionRecordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(
                List.of(
                    factory.generateObject(EvaluatedDecisionValue.class),
                    factory.generateObject(EvaluatedDecisionValue.class)))
            .build();

    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.FAILED)
                    .withValue(decisionRecordValue)
                    .withKey(recordKey));

    // when
    DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    // then
    assertThat(decisionInstanceEntity.getId()).isEqualTo(recordKey + "-1");
    assertThat(decisionInstanceEntity.getKey()).isEqualTo(recordKey);
    assertThat(decisionInstanceEntity.getState()).isEqualTo(DecisionInstanceState.EVALUATED);

    decisionInstanceEntity = new DecisionInstanceEntity().setId(recordKey + "-2");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    assertThat(decisionInstanceEntity.getId()).isEqualTo(recordKey + "-2");
    assertThat(decisionInstanceEntity.getKey()).isEqualTo(recordKey);
    assertThat(decisionInstanceEntity.getState()).isEqualTo(DecisionInstanceState.FAILED);
  }

  @Test
  void shouldUpdateEntityWithBigInputAndOutput() {
    // given
    final String largePayload = generateLargePayload();
    final long recordKey = 123L;
    final ImmutableEvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionType(DecisionType.DECISION_TABLE.name())
            .withMatchedRules(
                List.of(
                    ImmutableMatchedRuleValue.builder()
                        .from(factory.generateObject(MatchedRuleValue.class))
                        .withEvaluatedOutputs(
                            List.of(
                                factory
                                    .generateObject(ImmutableEvaluatedOutputValue.class)
                                    .withOutputValue(largePayload)))
                        .build()))
            .withEvaluatedInputs(
                List.of(
                    factory
                        .generateObject(ImmutableEvaluatedInputValue.class)
                        .withInputValue(largePayload)))
            .build();

    final DecisionEvaluationRecordValue decisionRecordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(factory.generateObject(DecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(List.of(evaluatedDecision))
            .build();

    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionRecordValue)
                    .withKey(recordKey));

    // when
    final DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    // then

    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getId())
        .isEqualTo(
            evaluatedDecision
                .getMatchedRules()
                .getFirst()
                .getEvaluatedOutputs()
                .getFirst()
                .getOutputId());

    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getName())
        .isEqualTo(
            evaluatedDecision
                .getMatchedRules()
                .getFirst()
                .getEvaluatedOutputs()
                .getFirst()
                .getOutputName());

    assertThat(decisionInstanceEntity.getEvaluatedOutputs().getFirst().getValue())
        .isEqualTo(largePayload);

    assertThat(decisionInstanceEntity.getEvaluatedInputs()).isNotEmpty();
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getId())
        .isEqualTo(evaluatedDecision.getEvaluatedInputs().getFirst().getInputId());
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getName())
        .isEqualTo(evaluatedDecision.getEvaluatedInputs().getFirst().getInputName());
    assertThat(decisionInstanceEntity.getEvaluatedInputs().getFirst().getValue())
        .isEqualTo(largePayload);
  }

  private static String generateLargePayload() {
    final String fillChar = "a";
    return fillChar.repeat(42 * 1024);
  }
}
