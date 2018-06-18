/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExclusiveGatewayTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Test
  public void shouldEvaluateConditionOnFlow() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlow(s -> s.condition("$.foo < 5"))
            .endEvent("a")
            .sequenceFlow(s -> s.defaultFlow())
            .endEvent("b")
            .done();

    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflowDefinition, "workflow.bpmn")
        .send()
        .join();

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow")
        .latestVersion()
        .payload("{\"foo\":3}")
        .send()
        .join();

    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));

    final WorkflowInstanceEvent endEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.END_EVENT_OCCURRED);
    assertThat(endEvent.getActivityId()).isEqualTo("a");
  }

  @Test
  public void shouldTakeDefaultFlow() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlow(s -> s.condition("$.foo < 5"))
            .endEvent("a")
            .sequenceFlow(s -> s.defaultFlow())
            .endEvent("b")
            .done();

    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflowDefinition, "workflow.bpmn")
        .send()
        .join();

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow")
        .latestVersion()
        .payload("{\"foo\":7}")
        .send()
        .join();

    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));

    final WorkflowInstanceEvent endEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.END_EVENT_OCCURRED);
    assertThat(endEvent.getActivityId()).isEqualTo("b");
  }

  @Test
  public void shouldExecuteWorkflowWithLoop() {
    // given
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
            .startEvent()
            .serviceTask("inc", t -> t.taskType("inc"))
            .exclusiveGateway()
            .sequenceFlow(s -> s.condition("$.count > 5"))
            .endEvent()
            .sequenceFlow("back", s -> s.defaultFlow())
            .joinWith("inc")
            .done();

    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflowDefinition, "workflow.bpmn")
        .send()
        .join();

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow")
        .latestVersion()
        .payload("{\"count\":0}")
        .send()
        .join();

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("inc")
        .handler(
            (client, job) -> {
              final String payload = job.getPayload();
              final int i = payload.indexOf(":");
              final int count = Integer.valueOf(payload.substring(i + 1, i + 2));

              client.newCompleteCommand(job).payload("{\"count\":" + (count + 1) + "}").send();
            })
        .open();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));

    final WorkflowInstanceEvent event =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED);
    assertThat(event.getPayload()).isEqualTo("{\"count\":6}");
  }
}
