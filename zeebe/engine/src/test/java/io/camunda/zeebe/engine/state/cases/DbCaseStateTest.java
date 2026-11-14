/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbCaseStateTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableCaseState caseState;

  @BeforeEach
  void setUp() {
    caseState = processingState.getCaseState();
  }

  @Test
  void shouldNotFindUnknownCase() {
    // when
    final var exists = caseState.exists(1L);
    final var persisted = caseState.getCase(1L);

    // then
    assertThat(exists).isFalse();
    assertThat(persisted).isEmpty();
  }

  @Test
  void shouldReadBackCreatedCase() {
    // given
    final var record =
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setTenantId("tenant")
            .setBusinessId("business")
            .setTitle("title");

    // when
    caseState.create(1L, record);

    // then
    assertThat(caseState.exists(1L)).isTrue();
    assertThat(caseState.getCase(1L))
        .hasValueSatisfying(
            persisted -> {
              assertThat(persisted.getBpmnProcessId()).isEqualTo("process");
              assertThat(persisted.getTenantId()).isEqualTo("tenant");
              assertThat(persisted.getBusinessId()).isEqualTo("business");
              assertThat(persisted.getTitle()).isEqualTo("title");
            });
  }

  @Test
  void shouldKeepCasesApart() {
    // given
    caseState.create(
        1L,
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setTitle("first"));
    caseState.create(
        2L,
        new CaseRecord()
            .setProcessInstanceKey(2L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setTitle("second"));

    // then
    assertThat(caseState.getCase(1L).orElseThrow().getTitle()).isEqualTo("first");
    assertThat(caseState.getCase(2L).orElseThrow().getTitle()).isEqualTo("second");
  }

  @Test
  void shouldReadBackStagesOfCreatedCase() {
    // given
    final var record =
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .addStage("stage-1", CaseStageStatus.NOT_STARTED)
            .addStage("stage-2", CaseStageStatus.ACTIVE);

    // when
    caseState.create(1L, record);

    // then
    assertThat(stagesOf(1L))
        .containsExactlyInAnyOrder(
            tuple("stage-1", CaseStageStatus.NOT_STARTED),
            tuple("stage-2", CaseStageStatus.ACTIVE));
  }

  @Test
  void shouldOnlyReadStagesOfTheRequestedCase() {
    // given
    caseState.create(
        1L,
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .addStage("stage-1", CaseStageStatus.NOT_STARTED));
    caseState.create(
        2L,
        new CaseRecord()
            .setProcessInstanceKey(2L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .addStage("stage-1", CaseStageStatus.COMPLETED));

    // then
    assertThat(stagesOf(1L)).containsExactly(tuple("stage-1", CaseStageStatus.NOT_STARTED));
    assertThat(stagesOf(2L)).containsExactly(tuple("stage-1", CaseStageStatus.COMPLETED));
  }

  @Test
  void shouldReadNoStagesOfCaseWithoutStages() {
    // given
    caseState.create(
        1L,
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process"));

    // then
    assertThat(stagesOf(1L)).isEmpty();
  }

  @Test
  void shouldUpdateBusinessId() {
    // given
    caseState.create(
        1L,
        new CaseRecord()
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setBusinessId("old")
            .setTitle("title"));

    // when
    caseState.updateBusinessId(1L, "new");

    // then
    final var persisted = caseState.getCase(1L).orElseThrow();
    assertThat(persisted.getBusinessId()).isEqualTo("new");
    assertThat(persisted.getTitle()).isEqualTo("title");
  }

  private List<Object> stagesOf(final long caseKey) {
    final var stages = new ArrayList<Object>();
    caseState.forEachStage(
        caseKey, (elementId, stage) -> stages.add(tuple(elementId, stage.getStatus())));
    return stages;
  }
}
