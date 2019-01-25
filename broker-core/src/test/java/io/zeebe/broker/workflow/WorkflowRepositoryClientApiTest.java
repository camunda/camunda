/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_KEY;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ErrorResponseException;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowRepositoryClientApiTest {
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldRequestWorkflowByKey() {
    final ExecuteCommandResponse deployment =
        apiRule.partitionClient().deployWithResponse(WORKFLOW, "wf.bpmn");

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(deployment, 0);

    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.GET_WORKFLOW)
            .data()
            .put(PROP_WORKFLOW_KEY, deployedWorkflow.get(PROP_WORKFLOW_KEY))
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "")
            .put(PROP_WORKFLOW_VERSION, -1)
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();

    assertThat(data.get(PROP_WORKFLOW_KEY)).isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_KEY));
    assertThat(data.get(PROP_WORKFLOW_VERSION))
        .isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_VERSION));
    assertThat(data.get(PROP_WORKFLOW_BPMN_PROCESS_ID))
        .isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_BPMN_PROCESS_ID));
    assertThat(data.get("resourceName")).isEqualTo("wf.bpmn");
    assertThat((String) data.get("bpmnXml")).isNotEmpty();
  }

  @Test
  public void shouldNotGetWorkflowByNonExistingKey() {
    assertThatThrownBy(
            () ->
                apiRule
                    .createControlMessageRequest()
                    .messageType(ControlMessageType.GET_WORKFLOW)
                    .data()
                    .put(PROP_WORKFLOW_KEY, 1231)
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "")
                    .put(PROP_WORKFLOW_VERSION, -1)
                    .done()
                    .sendAndAwait())
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKFLOW_NOT_FOUND);
  }

  @Test
  public void shouldNotGetWorkflowByNonExistingBpmnProcessKey() {
    assertThatThrownBy(
            () ->
                apiRule
                    .createControlMessageRequest()
                    .messageType(ControlMessageType.GET_WORKFLOW)
                    .data()
                    .put(PROP_WORKFLOW_KEY, -1)
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "notExisting")
                    .put(PROP_WORKFLOW_VERSION, -1)
                    .done()
                    .sendAndAwait())
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKFLOW_NOT_FOUND);
  }

  @Test
  public void shouldNotGetWorkflowByNonExistingBpmnProcessKeyAndVersion() {
    assertThatThrownBy(
            () ->
                apiRule
                    .createControlMessageRequest()
                    .messageType(ControlMessageType.GET_WORKFLOW)
                    .data()
                    .put(PROP_WORKFLOW_KEY, -1)
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "notExisting")
                    .put(PROP_WORKFLOW_VERSION, 99)
                    .done()
                    .sendAndAwait())
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKFLOW_NOT_FOUND);
  }

  @Test
  public void shouldNotGetWorkflowByExistingBpmnProcessKeyAndNonExistingVersion() {
    apiRule.partitionClient().deployWithResponse(WORKFLOW);

    assertThatThrownBy(
            () ->
                apiRule
                    .createControlMessageRequest()
                    .messageType(ControlMessageType.GET_WORKFLOW)
                    .data()
                    .put(PROP_WORKFLOW_KEY, -1)
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_VERSION, 99)
                    .done()
                    .sendAndAwait())
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKFLOW_NOT_FOUND);
  }

  @Test
  public void shouldRequestLatestWorkflowBpmnProcessId() {
    // given
    final ExecuteCommandResponse deployment1 =
        apiRule.partitionClient().deployWithResponse(WORKFLOW);

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(deployment1, 0);

    // when

    final Map<String, Object> response1 =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.GET_WORKFLOW)
            .data()
            .put(PROP_WORKFLOW_KEY, -1)
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, -1)
            .done()
            .sendAndAwait()
            .getData();

    // then

    assertThat(response1.get(PROP_WORKFLOW_KEY)).isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_KEY));

    // and when

    final ExecuteCommandResponse deployment2 =
        apiRule.partitionClient().deployWithResponse(WORKFLOW);

    final Map<String, Object> deployedWorkflow2 = getDeployedWorkflow(deployment2, 0);

    final Map<String, Object> response2 =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.GET_WORKFLOW)
            .data()
            .put(PROP_WORKFLOW_KEY, -1)
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, -1)
            .done()
            .sendAndAwait()
            .getData();

    assertThat(response2.get(PROP_WORKFLOW_KEY))
        .isEqualTo(deployedWorkflow2.get(PROP_WORKFLOW_KEY));
  }

  @Test
  public void shouldGetWorkflowVersionBpmnProcessId() {
    // given
    final ExecuteCommandResponse deployment1 =
        apiRule.partitionClient().deployWithResponse(WORKFLOW);

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(deployment1, 0);

    // when

    final Map<String, Object> response1 =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.GET_WORKFLOW)
            .data()
            .put(PROP_WORKFLOW_KEY, -1)
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, 1)
            .done()
            .sendAndAwait()
            .getData();

    // then

    assertThat(response1.get(PROP_WORKFLOW_KEY)).isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_KEY));

    // and when

    final ExecuteCommandResponse deployment2 =
        apiRule.partitionClient().deployWithResponse(WORKFLOW);

    final Map<String, Object> deployedWorkflow2 = getDeployedWorkflow(deployment2, 0);

    final Map<String, Object> response2 =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.GET_WORKFLOW)
            .data()
            .put(PROP_WORKFLOW_KEY, -1)
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, 2)
            .done()
            .sendAndAwait()
            .getData();

    assertThat(response2.get(PROP_WORKFLOW_KEY))
        .isEqualTo(deployedWorkflow2.get(PROP_WORKFLOW_KEY));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListWorkflowsIfNothingDeployed() throws Exception {
    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "")
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();
    final List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListWorkflowsByBpmnProcessIdIfNothingDeployed() {
    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "nonExisting")
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();
    final List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListWorkflow() {
    final ExecuteCommandResponse deployment =
        apiRule.partitionClient().deployWithResponse(WORKFLOW, "wf.bpmn");

    final Map<String, Object> deployedWorkflow = getDeployedWorkflow(deployment, 0);

    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "")
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();
    final List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(1);

    final Map<String, Object> theWorkflow = workflows.get(0);

    assertThat(theWorkflow.get(PROP_WORKFLOW_KEY))
        .isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_KEY));
    assertThat(theWorkflow.get(PROP_WORKFLOW_VERSION))
        .isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_VERSION));
    assertThat(theWorkflow.get(PROP_WORKFLOW_BPMN_PROCESS_ID))
        .isEqualTo(deployedWorkflow.get(PROP_WORKFLOW_BPMN_PROCESS_ID));
    assertThat(theWorkflow.get("resourceName")).isEqualTo("wf.bpmn");
    assertThat(theWorkflow.containsKey("bpmnXml")).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListWorkflowsByBpmnProcessId() {
    apiRule.partitionClient().deployWithResponse(WORKFLOW);
    apiRule.partitionClient().deployWithResponse(WORKFLOW_2);

    ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .done()
            .sendAndAwait();

    Map<String, Object> data = requestWorkflowResponse.getData();
    List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(1);
    assertThat(workflows.get(0).get(PROP_WORKFLOW_BPMN_PROCESS_ID)).isEqualTo("process");

    requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process2")
            .done()
            .sendAndAwait();

    data = requestWorkflowResponse.getData();
    workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(1);
    assertThat(workflows.get(0).get(PROP_WORKFLOW_BPMN_PROCESS_ID)).isEqualTo("process2");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListWorkflowsByBpmnProcessIdNonExisting() {
    apiRule.partitionClient().deployWithResponse(WORKFLOW);
    apiRule.partitionClient().deployWithResponse(WORKFLOW_2);

    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "nonExisting")
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();
    final List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldListMultipleWorkflows() {
    apiRule.partitionClient().deployWithResponse(WORKFLOW);
    apiRule.partitionClient().deployWithResponse(WORKFLOW);
    apiRule.partitionClient().deployWithResponse(WORKFLOW);

    final ControlMessageResponse requestWorkflowResponse =
        apiRule
            .createControlMessageRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .messageType(ControlMessageType.LIST_WORKFLOWS)
            .data()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "")
            .done()
            .sendAndAwait();

    final Map<String, Object> data = requestWorkflowResponse.getData();
    final List<Map<String, Object>> workflows = (List<Map<String, Object>>) data.get("workflows");
    assertThat(workflows.size()).isEqualTo(3);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getDeployedWorkflow(
      final ExecuteCommandResponse d1, final int offset) {
    final List<Map<String, Object>> d1Workflows =
        (List<Map<String, Object>>) d1.getValue().get("workflows");
    return d1Workflows.get(offset);
  }
}
