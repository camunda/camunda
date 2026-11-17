/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class CaseCreatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableCaseState caseState;
  private CaseCreatedApplier applier;

  @BeforeEach
  void setUp() {
    caseState = processingState.getCaseState();
    applier = new CaseCreatedApplier(caseState);
  }

  @Test
  void shouldStoreCaseUnderTheKeyOfTheEvent() {
    // given
    final long processInstanceKey = 42L;
    final var record =
        new CaseRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setTenantId("tenant")
            .setBusinessId("business")
            .setTitle("title");

    // when
    applier.applyState(processInstanceKey, record);

    // then
    assertThat(caseState.exists(processInstanceKey)).isTrue();
    final var persisted = caseState.getCase(processInstanceKey).orElseThrow();
    assertThat(persisted.getBpmnProcessId()).isEqualTo("process");
    assertThat(persisted.getTenantId()).isEqualTo("tenant");
    assertThat(persisted.getBusinessId()).isEqualTo("business");
    assertThat(persisted.getTitle()).isEqualTo("title");
  }

  @Test
  void shouldStoreStagesOfTheCase() {
    // given
    final long processInstanceKey = 42L;
    final var record =
        new CaseRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .addStage("stage", CaseStageStatus.ACTIVE);

    // when
    applier.applyState(processInstanceKey, record);

    // then
    final var stages = new ArrayList<String>();
    caseState.forEachStage(
        processInstanceKey, (elementId, stage) -> stages.add(elementId + "/" + stage.getStatus()));
    assertThat(stages).containsExactly("stage/ACTIVE");
  }
}
