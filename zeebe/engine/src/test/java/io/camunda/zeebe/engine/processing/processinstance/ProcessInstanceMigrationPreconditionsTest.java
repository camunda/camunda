/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.isAdHocRelatedProcess;
import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.isAdHocSubProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link ProcessInstanceMigrationPreconditions} focusing on Ad-Hoc Sub-Process
 * validation
 */
public class ProcessInstanceMigrationPreconditionsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private ProcessState processState;

  @Before
  public void setUp() {
    processState = ENGINE.getProcessingState().getProcessState();
  }

  // ==================== isAdHocSubProcess Tests ====================

  @Test
  public void shouldReturnTrueForAdHocSubProcessElementType() {
    // when/then
    assertThat(isAdHocSubProcess(BpmnElementType.AD_HOC_SUB_PROCESS)).isTrue();
  }

  @Test
  public void shouldReturnFalseForNonAdHocSubProcessElementType() {
    // when/then
    assertThat(isAdHocSubProcess(BpmnElementType.SERVICE_TASK)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.SUB_PROCESS)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.PROCESS)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.USER_TASK)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.CALL_ACTIVITY)).isFalse();
  }

  // ==================== isAdHocRelatedProcess Tests ====================

  @Test
  public void shouldReturnTrueForAdHocSubProcess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(
                        "adHocSubProcess",
                        ahsp -> ahsp.serviceTask("task", t -> t.zeebeJobType("task")))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final DeployedProcess deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, "<default>");

    // when/then
    assertThat(isAdHocRelatedProcess(deployedProcess, "adHocSubProcess")).isTrue();
  }

  @Test
  public void shouldReturnFalseForNonAdHocSubProcess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final DeployedProcess deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, "<default>");

    // when/then
    assertThat(isAdHocRelatedProcess(deployedProcess, "task")).isFalse();
  }

  @Test
  public void shouldReturnTrueForMultiInstanceAdHocSubProcess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(
                        "adHocSubProcess",
                        ahsp -> ahsp.serviceTask("task", t -> t.zeebeJobType("task")))
                    .multiInstance(
                        mi ->
                            mi.zeebeInputCollectionExpression("[1,2,3]").zeebeInputElement("item"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final DeployedProcess deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, "<default>");

    // when/then - should return true for the multi-instance body wrapping ad-hoc subprocess
    assertThat(isAdHocRelatedProcess(deployedProcess, "adHocSubProcess")).isTrue();
  }

  @Test
  public void shouldReturnFalseForMultiInstanceNonAdHocSubProcess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .multiInstance(
                        mi ->
                            mi.zeebeInputCollectionExpression("[1,2,3]").zeebeInputElement("item"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final DeployedProcess deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, "<default>");

    // when/then - should return false for the multi-instance body wrapping non ad-hoc subprocess
    assertThat(isAdHocRelatedProcess(deployedProcess, "task")).isFalse();
  }
}
