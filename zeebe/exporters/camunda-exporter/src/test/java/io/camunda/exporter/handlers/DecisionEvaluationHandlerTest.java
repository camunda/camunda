/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
  void shouldExtractIdAndIndexes() {
    // given
    final TargetIndexLocator indexLocator = mock(TargetIndexLocator.class);
    final TargetIndex index = TargetIndex.mainIndex(indexName);
    when(indexLocator.locateOrdinalIndex(eq(indexName), any())).thenReturn(index);
    final ImmutableEvaluatedDecisionValue decisionValue =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionEvaluationInstanceKey("123-1")
            .build();
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

    // when - then
    assertThat(underTest.extractIdAndIndexes(indexLocator, decisionRecord))
        .containsExactly(new IdAndIndex("123-1", index));
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
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(index, inputEntity);
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
            .withDecisionEvaluationInstanceKey(recordKey + "-1")
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
            .withBusinessId("order-123")
            .withStorageOrdinal(1001)
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
        .isEqualTo(DecisionType.fromString(decisionType.name()));
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
    assertThat(decisionInstanceEntity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(decisionRecordValue.getRootProcessInstanceKey());
    assertThat(decisionInstanceEntity.getBusinessId())
        .isEqualTo(decisionRecordValue.getBusinessId());
    assertThat(decisionInstanceEntity.getStorageOrdinal()).isEqualTo(1001);
  }

  @Test
  void shouldNotSetStorageOrdinalWhenDefault() {
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
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionRecordValue)
                    .withKey(recordKey));

    // when
    final DecisionInstanceEntity decisionInstanceEntity =
        new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(decisionRecord, decisionInstanceEntity);

    // then
    assertThat(decisionInstanceEntity.getStorageOrdinal()).isNull();
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
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
            .withRootProcessInstanceKey(-1L)
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
    assertThat(decisionInstanceEntity.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldNotSetBusinessIdWhenEmpty() {
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
            .withBusinessId("")
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
    assertThat(decisionInstanceEntity.getBusinessId()).isNull();
  }

  @Test
  void shouldUpdateEntityFromRecordWithStateFailed() {
    // given
    final long recordKey = 123L;
    final ImmutableEvaluatedDecisionValue evaluatedDecision =
        ImmutableEvaluatedDecisionValue.builder()
            .from(factory.generateObject(EvaluatedDecisionValue.class))
            .withDecisionType(DecisionType.DECISION_TABLE.name())
            .withDecisionEvaluationInstanceKey(recordKey + "-1")
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
                    ImmutableEvaluatedDecisionValue.builder()
                        .from(factory.generateObject(EvaluatedDecisionValue.class))
                        .withDecisionEvaluationInstanceKey(recordKey + "-1")
                        .build(),
                    ImmutableEvaluatedDecisionValue.builder()
                        .from(factory.generateObject(EvaluatedDecisionValue.class))
                        .withDecisionEvaluationInstanceKey(recordKey + "-2")
                        .build()))
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
            .withDecisionEvaluationInstanceKey(recordKey + "-1")
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

  @Test
  void shouldReadEvaluatedDecisionsOncePerRecord() {
    // given - getEvaluatedDecisions() copies every decision on each call in the broker, and the
    // batch writer calls generateIds once plus updateEntity once per decision of the same record
    final long recordKey = 123L;
    final DecisionEvaluationRecordValue value =
        mock(
            DecisionEvaluationRecordValue.class,
            delegatesTo(decisionEvaluationValue(recordKey, "d1", "d2", "d3")));
    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r -> r.withIntent(DecisionEvaluationIntent.FAILED).withValue(value).withKey(recordKey));

    // when
    final List<DecisionInstanceEntity> entities =
        underTest.generateIds(decisionRecord).stream()
            .map(
                id -> {
                  final var entity = underTest.createNewEntity(id);
                  underTest.updateEntity(decisionRecord, entity);
                  return entity;
                })
            .toList();

    // then
    assertThat(entities)
        .extracting(
            DecisionInstanceEntity::getDecisionId,
            DecisionInstanceEntity::getExecutionIndex,
            DecisionInstanceEntity::getState)
        .containsExactly(
            tuple("d1", 1, DecisionInstanceState.EVALUATED),
            tuple("d2", 2, DecisionInstanceState.EVALUATED),
            tuple("d3", 3, DecisionInstanceState.FAILED));
    verify(value, times(1)).getEvaluatedDecisions();
  }

  @ParameterizedTest
  @CsvSource({"2, 10, 123", "1, 11, 123", "1, 10, 124"})
  void shouldNotReuseEvaluatedDecisionsOfPreviousRecord(
      final int partitionId, final long position, final long recordKey) {
    // given - the first record keeps its decisions cached because only one of two is exported
    final Record<DecisionEvaluationRecordValue> firstRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionEvaluationValue(123L, "first", "other"))
                    .withKey(123L)
                    .withPartitionId(1)
                    .withPosition(10L));
    final Record<DecisionEvaluationRecordValue> secondRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionEvaluationValue(recordKey, "second", "other"))
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position));
    underTest.updateEntity(firstRecord, new DecisionInstanceEntity().setId("123-1"));

    // when
    final DecisionInstanceEntity entity = new DecisionInstanceEntity().setId(recordKey + "-1");
    underTest.updateEntity(secondRecord, entity);

    // then
    assertThat(entity.getDecisionId()).isEqualTo("second");
  }

  @Test
  void shouldGenerateLegacyIdsWhenDecisionEvaluationInstanceKeyIsMissing() {
    // given - records written by older Zeebe versions have no decisionEvaluationInstanceKey
    final long recordKey = 123L;
    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(legacyDecisionEvaluationValue("d1", "d2"))
                    .withKey(recordKey));

    // when
    final List<DecisionInstanceEntity> entities =
        underTest.generateIds(decisionRecord).stream()
            .map(
                id -> {
                  final var entity = underTest.createNewEntity(id);
                  underTest.updateEntity(decisionRecord, entity);
                  return entity;
                })
            .toList();

    // then
    assertThat(entities)
        .extracting(DecisionInstanceEntity::getId, DecisionInstanceEntity::getDecisionId)
        .containsExactly(tuple("123-1", "d1"), tuple("123-2", "d2"));
  }

  @Test
  void shouldRejectIdNotMatchingAnyEvaluatedDecision() {
    // given
    final long recordKey = 123L;
    final Record<DecisionEvaluationRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(decisionEvaluationValue(recordKey, "d1"))
                    .withKey(recordKey));
    final DecisionInstanceEntity entity = new DecisionInstanceEntity().setId(recordKey + "-01");

    // when - then
    assertThatThrownBy(() -> underTest.updateEntity(decisionRecord, entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("123-01");
  }

  private DecisionEvaluationRecordValue legacyDecisionEvaluationValue(final String... decisionIds) {
    return decisionEvaluationValue(index -> "", decisionIds);
  }

  private DecisionEvaluationRecordValue decisionEvaluationValue(
      final long recordKey, final String... decisionIds) {
    return decisionEvaluationValue(index -> recordKey + "-" + index, decisionIds);
  }

  private DecisionEvaluationRecordValue decisionEvaluationValue(
      final IntFunction<String> instanceKeyOfIndex, final String... decisionIds) {
    final var decisions = new ArrayList<EvaluatedDecisionValue>();
    for (int i = 0; i < decisionIds.length; i++) {
      decisions.add(
          ImmutableEvaluatedDecisionValue.builder()
              .from(factory.generateObject(EvaluatedDecisionValue.class))
              .withDecisionId(decisionIds[i])
              .withDecisionType(DecisionType.DECISION_TABLE.name())
              .withDecisionEvaluationInstanceKey(instanceKeyOfIndex.apply(i + 1))
              .build());
    }
    return ImmutableDecisionEvaluationRecordValue.builder()
        .from(factory.generateObject(DecisionEvaluationRecordValue.class))
        .withEvaluatedDecisions(decisions)
        .build();
  }

  private static String generateLargePayload() {
    final String fillChar = "a";
    return fillChar.repeat(42 * 1024);
  }
}
