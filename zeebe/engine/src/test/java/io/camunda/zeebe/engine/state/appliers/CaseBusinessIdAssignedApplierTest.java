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
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class CaseBusinessIdAssignedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableCaseState caseState;
  private CaseBusinessIdAssignedApplier applier;

  @BeforeEach
  public void setup() {
    caseState = processingState.getCaseState();
    applier = new CaseBusinessIdAssignedApplier(processingState);
  }

  @Test
  void shouldWriteCaseFromEvent() {
    // given
    final long caseKey = 10L;
    final long processInstanceKey = 11L;
    final var caseRecord = newCase(caseKey, processInstanceKey);
    caseState.createCase(caseRecord);
    caseRecord.setBusinessId("B-1");

    // when
    applier.applyState(caseKey, caseRecord);

    // then
    final var stored = caseState.getCaseByProcessInstanceKey(processInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getBusinessId()).isEqualTo("B-1");
    assertThat(stored.getTitle()).isEqualTo("Loan for ACME");
    assertThat(stored.getStages()).hasSize(1);
  }

  private static CaseRecord newCase(final long caseKey, final long processInstanceKey) {
    return new CaseRecord()
        .setCaseKey(caseKey)
        .setState(CaseLifecycleState.ACTIVE)
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionKey(456L)
        .setBpmnProcessId("loan-process")
        .setTitle("Loan for ACME")
        .addStage("review", "Review", CaseStageStatus.NOT_STARTED);
  }
}
