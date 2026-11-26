/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class CaseCreatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableCaseState caseState;
  private CaseCreatedApplier applier;

  @BeforeEach
  public void setup() {
    caseState = processingState.getCaseState();
    applier = new CaseCreatedApplier(processingState);
  }

  @Test
  void shouldStoreCase() {
    // given
    final long caseKey = 10L;
    final long processInstanceKey = 11L;
    final var record =
        new CaseRecord()
            .setCaseKey(caseKey)
            .setState(CaseLifecycleState.ACTIVE)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(456L)
            .setBpmnProcessId("loan-process")
            .setTitle("Loan for ACME")
            .addStage("review", "Review", CaseStageState.NOT_STARTED);

    // when
    applier.applyState(caseKey, record);

    // then
    final var stored = caseState.getCaseByProcessInstanceKey(processInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getCaseKey()).isEqualTo(caseKey);
    assertThat(stored.getBpmnProcessId()).isEqualTo("loan-process");
    assertThat(stored.getTitle()).isEqualTo("Loan for ACME");
    assertThat(stored.getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(tuple("review", "Review", CaseStageState.NOT_STARTED));
  }
}
