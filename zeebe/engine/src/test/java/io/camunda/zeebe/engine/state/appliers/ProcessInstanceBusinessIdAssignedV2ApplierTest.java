/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProcessInstanceBusinessIdAssignedV2ApplierTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;
  private static final String PROCESS_ID = "process";
  private static final String TENANT_ID = "<default>";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableCaseState caseState;
  private ProcessInstanceBusinessIdAssignedV2Applier applier;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    caseState = processingState.getCaseState();
    applier = new ProcessInstanceBusinessIdAssignedV2Applier(elementInstanceState, caseState);

    elementInstanceState.createInstance(
        new ElementInstance(
            PROCESS_INSTANCE_KEY,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new ProcessInstanceRecord()
                .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                .setBpmnProcessId(PROCESS_ID)
                .setTenantId(TENANT_ID)));
  }

  @Test
  void shouldUpdateBusinessIdOfCase() {
    // given
    caseState.create(
        PROCESS_INSTANCE_KEY,
        new CaseRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process")
            .setTitle("title"));

    // when
    applier.applyState(PROCESS_INSTANCE_KEY, assignment("business"));

    // then
    final var persisted = caseState.getCase(PROCESS_INSTANCE_KEY).orElseThrow();
    assertThat(persisted.getBusinessId()).isEqualTo("business");
    assertThat(persisted.getTitle()).isEqualTo("title");
  }

  @Test
  void shouldNotCreateCaseForProcessInstanceThatIsNoCase() {
    // when
    applier.applyState(PROCESS_INSTANCE_KEY, assignment("business"));

    // then
    assertThat(caseState.exists(PROCESS_INSTANCE_KEY)).isFalse();
  }

  @Test
  void shouldUpdateElementInstanceAndIndexWhenProcessInstanceIsCase() {
    // given
    caseState.create(
        PROCESS_INSTANCE_KEY,
        new CaseRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(7L)
            .setBpmnProcessId("process"));

    // when
    applier.applyState(PROCESS_INSTANCE_KEY, assignment("business"));

    // then
    assertBusinessIdOfProcessInstance("business");
  }

  @Test
  void shouldUpdateElementInstanceAndIndexWhenProcessInstanceIsNoCase() {
    // when
    applier.applyState(PROCESS_INSTANCE_KEY, assignment("business"));

    // then
    assertBusinessIdOfProcessInstance("business");
  }

  private void assertBusinessIdOfProcessInstance(final String businessId) {
    assertThat(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY).getValue().getBusinessId())
        .isEqualTo(businessId);
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                businessId, PROCESS_ID, TENANT_ID, key -> false))
        .isTrue();
  }

  private static ProcessInstanceBusinessIdRecord assignment(final String businessId) {
    return new ProcessInstanceBusinessIdRecord()
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setBpmnProcessId(PROCESS_ID)
        .setTenantId(TENANT_ID)
        .setBusinessId(businessId);
  }
}
