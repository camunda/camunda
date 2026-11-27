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
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DbCaseStateTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableCaseState caseState;

  @BeforeEach
  public void setup() {
    caseState = processingState.getCaseState();
  }

  @Test
  void shouldCreateCase() {
    // given
    final var caseRecord = newCase(10L, 11L);

    // when
    caseState.createCase(caseRecord);

    // then
    final var stored = caseState.getCaseByProcessInstanceKey(11L);
    assertThat(stored).isNotNull();
    assertThat(stored.getCaseKey()).isEqualTo(10L);
    assertThat(stored.getState()).isEqualTo(CaseLifecycleState.ACTIVE);
    assertThat(stored.getProcessInstanceKey()).isEqualTo(11L);
    assertThat(stored.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(stored.getBpmnProcessId()).isEqualTo("loan-process");
    assertThat(stored.getTenantId()).isEqualTo("tenant-1");
    assertThat(stored.getBusinessId()).isEmpty();
    assertThat(stored.getTitle()).isEqualTo("Loan for ACME");
    assertThat(stored.getStorageOrdinal()).isEqualTo(7);
    assertThat(stored.getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(
            tuple("review", "Review", CaseStageState.NOT_STARTED),
            tuple("ship", "Ship", CaseStageState.NOT_STARTED));
  }

  @Test
  void shouldFindCaseByProcessInstanceKey() {
    // given
    caseState.createCase(newCase(20L, 21L));

    // when
    final var byProcessInstanceKey = caseState.getCaseByProcessInstanceKey(21L);

    // then
    assertThat(byProcessInstanceKey).isNotNull();
    assertThat(byProcessInstanceKey.getCaseKey()).isEqualTo(20L);
    assertThat(byProcessInstanceKey.getProcessInstanceKey()).isEqualTo(21L);
  }

  @Test
  void shouldReturnNullForUnknownProcessInstance() {
    // when
    final var stored = caseState.getCaseByProcessInstanceKey(99L);

    // then
    assertThat(stored).isNull();
  }

  @Test
  void shouldGetCaseByKey() {
    // given
    caseState.createCase(newCase(30L, 31L));

    // when
    final var stored = caseState.getCase(30L);

    // then
    assertThat(stored).isNotNull();
    assertThat(stored.getCaseKey()).isEqualTo(30L);
    assertThat(stored.getProcessInstanceKey()).isEqualTo(31L);
  }

  @Test
  void shouldReturnIndependentInstances() {
    // given
    caseState.createCase(newCase(50L, 51L));
    final var first = caseState.getCase(50L);
    final var second = caseState.getCase(50L);

    // when
    first.setBusinessId("changed");

    // then
    assertThat(second).isNotSameAs(first);
    assertThat(second.getBusinessId()).isEmpty();
    assertThat(caseState.getCase(50L).getBusinessId()).isEmpty();
  }

  @Test
  void shouldReturnNullForUnknownCaseKey() {
    // given
    caseState.createCase(newCase(40L, 41L));

    // when
    final var stored = caseState.getCase(41L);

    // then
    assertThat(stored).isNull();
  }

  @Test
  void shouldUpdateCase() {
    // given
    caseState.createCase(newCase(10L, 11L));

    // when
    final var updated = new CaseRecord();
    updated.copyFrom(caseState.getCase(10L));
    updated.setBusinessId("B-1");
    caseState.updateCase(updated);

    // then
    final var stored = caseState.getCase(10L);
    assertThat(stored).isNotNull();
    assertThat(stored.getBusinessId()).isEqualTo("B-1");
    assertThat(stored.getTitle()).isEqualTo("Loan for ACME");
    assertThat(stored.getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(
            tuple("review", "Review", CaseStageState.NOT_STARTED),
            tuple("ship", "Ship", CaseStageState.NOT_STARTED));
  }

  private static CaseRecord newCase(final long caseKey, final long processInstanceKey) {
    return new CaseRecord()
        .setCaseKey(caseKey)
        .setState(CaseLifecycleState.ACTIVE)
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionKey(456L)
        .setBpmnProcessId("loan-process")
        .setTenantId("tenant-1")
        .setTitle("Loan for ACME")
        .addStage("review", "Review", CaseStageState.NOT_STARTED)
        .addStage("ship", "Ship", CaseStageState.NOT_STARTED)
        .setStorageOrdinal(7);
  }
}
