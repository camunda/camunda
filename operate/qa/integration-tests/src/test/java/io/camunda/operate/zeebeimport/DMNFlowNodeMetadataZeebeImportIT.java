/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.rest.dto.metadata.BusinessRuleTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Test;

public class DMNFlowNodeMetadataZeebeImportIT extends OperateZeebeAbstractIT {

  @Test
  public void testDecisionsGrouped() throws Exception {
    // given
    final String bpmnProcessId = "process";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision1Name = "Invoice Classification";
    final String decision2Name = "Assign Approver Group";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task ->
                    task.zeebeCalledDecisionId(demoDecisionId2)
                        .zeebeResultVariable("approverGroups"))
            .done();

    Long processInstanceKey =
        tester
            .deployProcess(instance, "test.bpmn")
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .incidentIsActive()
            .getProcessInstanceKey();

    testNoDecisionDeployed(decision2Name, elementId, processInstanceKey);

    processInstanceKey =
        tester
            .deployDecision("invoiceBusinessDecisions_v_1.dmn")
            .waitUntil()
            .decisionsAreDeployed(2)
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .decisionInstancesAreCreated(1)
            .incidentIsActive()
            .getProcessInstanceKey();

    testDecisionIncident(decision2Name, elementId, processInstanceKey, decision1Name);

    processInstanceKey =
        tester
            .deployDecision("invoiceBusinessDecisions_v_1.dmn")
            .waitUntil()
            .decisionsAreDeployed(2)
            .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
            .waitUntil()
            // we have 2 decisions in this DRD
            .decisionInstancesAreCreated(3)
            .getProcessInstanceKey();

    testDecisionWithoutIncident(decision2Name, elementId, processInstanceKey);
  }

  private void testNoDecisionDeployed(
      final String decision2Name, final String elementId, final Long processInstanceKey)
      throws Exception {
    // when
    final FlowNodeMetadataDto flowNodeMetadata =
        tester.getFlowNodeMetadataFromRest(
            String.valueOf(processInstanceKey), elementId, null, null);

    // then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionDefinitionName())
        .isNull();
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionInstanceId())
        .isNull();
    assertThat(flowNodeMetadata.getIncident()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision()).isNull();
    assertThat(flowNodeMetadata.getIncident().getErrorType().getId())
        .isEqualTo(ErrorType.CALLED_DECISION_ERROR.name());
  }

  private void testDecisionIncident(
      final String decision2Name,
      final String elementId,
      final Long processInstanceKey,
      final String decision1Name)
      throws Exception {
    // when
    final FlowNodeMetadataDto flowNodeMetadata =
        tester.getFlowNodeMetadataFromRest(
            String.valueOf(processInstanceKey), elementId, null, null);

    // then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionDefinitionName())
        .isEqualTo(decision2Name);
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionInstanceId())
        .isNull();
    assertThat(flowNodeMetadata.getIncident()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getDecisionName())
        .isEqualTo(decision1Name);
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getInstanceId()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getRootCauseDecision().getInstanceId())
        .endsWith("-1");
    assertThat(flowNodeMetadata.getIncident().getErrorType().getId())
        .isEqualTo(ErrorType.DECISION_EVALUATION_ERROR.name());
  }

  private void testDecisionWithoutIncident(
      final String decision2Name, final String elementId, final Long processInstanceKey)
      throws Exception {
    // when
    final FlowNodeMetadataDto flowNodeMetadata =
        tester.getFlowNodeMetadataFromRest(
            String.valueOf(processInstanceKey), elementId, null, null);

    // then
    assertThat(flowNodeMetadata).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionDefinitionName())
        .isEqualTo(decision2Name);
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionInstanceId())
        .isNotNull();
    assertThat(
            ((BusinessRuleTaskInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata())
                .getCalledDecisionInstanceId())
        .endsWith("-2");
    assertThat(flowNodeMetadata.getIncident()).isNull();
  }
}
