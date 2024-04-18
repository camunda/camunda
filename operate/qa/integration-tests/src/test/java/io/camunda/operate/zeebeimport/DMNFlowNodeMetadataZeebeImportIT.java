/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.rest.dto.metadata.BusinessRuleTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
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
