/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Test;

public class DMNFlowNodeMetadataIT extends OperateZeebeIntegrationTest {


  @Test
  public void testDecisionsGrouped() throws Exception {
    //given
    final String bpmnProcessId = "process";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision1Name = "Invoice Classification";
    final String decision2Name = "Assign Approver Group";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(elementId, task -> task.zeebeCalledDecisionId(demoDecisionId2)
                .zeebeResultVariable("approverGroups"))
            .done();

    Long processInstanceKey = tester.deployProcess(instance, "test.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .incidentIsActive()
        .getProcessInstanceKey();

    testNoDecisionDeployed(decision2Name, elementId, processInstanceKey);

    processInstanceKey = tester
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        .decisionsAreDeployed(2)
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .decisionInstancesAreCreated(1)
        .incidentIsActive()
        .getProcessInstanceKey();

    testDecisionIncident(decision2Name, elementId, processInstanceKey, decision1Name);

    processInstanceKey = tester
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        .decisionsAreDeployed(2)
        .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
        .waitUntil()
        .decisionInstancesAreCreated(2)
        .getProcessInstanceKey();

    testDecisionWithoutIncident(decision2Name, elementId, processInstanceKey);
  }

  private void testNoDecisionDeployed(final String decision2Name, final String elementId,
      final Long processInstanceKey) throws Exception {
    //when
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), elementId, null, null);

    //then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionDefinitionName()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionInstanceId()).isNull();
    assertThat(flowNodeMetadata.getIncident()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision()).isNull();
    assertThat(flowNodeMetadata.getIncident().getErrorType().getId()).isEqualTo(
        ErrorType.CALLED_DECISION_ERROR.name());
  }

  private void testDecisionIncident(final String decision2Name, final String elementId,
      final Long processInstanceKey, final String decision1Name) throws Exception {
    //when
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), elementId, null, null);

    //then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionDefinitionName()).isEqualTo(
        decision2Name);
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionInstanceId()).isNull();
    assertThat(flowNodeMetadata.getIncident()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getDecisionName()).isEqualTo(decision1Name);
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getInstanceId()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getInstanceId()).endsWith("-1");
    assertThat(flowNodeMetadata.getIncident().getErrorType().getId()).isEqualTo(
        ErrorType.DECISION_EVALUATION_ERROR.name());
  }

  private void testDecisionWithoutIncident(final String decision2Name, final String elementId,
      final Long processInstanceKey) throws Exception {
    //when
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), elementId, null, null);

    //then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionDefinitionName()).isEqualTo(
        decision2Name);
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionInstanceId()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledDecisionInstanceId()).endsWith("-2");
    assertThat(flowNodeMetadata.getIncident()).isNull();
  }

}
