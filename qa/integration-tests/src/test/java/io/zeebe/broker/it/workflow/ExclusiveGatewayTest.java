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

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertEndEventOccurred;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExclusiveGatewayTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldEvaluateConditionOnFlow() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway()
            .condition("$.foo < 5")
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .endEvent("b")
            .done();

    deploy(workflowDefinition);

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow")
        .latestVersion()
        .payload("{\"foo\":3}")
        .send()
        .join();

    assertWorkflowInstanceCompleted("workflow");
    assertEndEventOccurred("a");
  }

  @Test
  public void shouldTakeDefaultFlow() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway()
            .condition("$.foo < 5")
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .endEvent("b")
            .done();

    deploy(workflowDefinition);

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow")
        .latestVersion()
        .payload("{\"foo\":7}")
        .send()
        .join();

    assertWorkflowInstanceCompleted("workflow");
    assertEndEventOccurred("b");
  }

  @Test
  public void shouldExecuteWorkflowWithLoop() {
    // given
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .serviceTask("inc", t -> t.zeebeTaskType("inc"))
            .exclusiveGateway()
            .condition("$.count > 5")
            .endEvent()
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .connectTo("inc")
            .done();

    deploy(workflowDefinition);

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

              client
                  .newCompleteCommand(job.getKey())
                  .payload("{\"count\":" + (count + 1) + "}")
                  .send();
            })
        .open();

    // then
    assertWorkflowInstanceCompleted(
        "workflow", event -> assertThat(event.getPayload()).isEqualTo("{\"count\":6}"));
  }

  private void deploy(BpmnModelInstance workflowDefinition) {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflowDefinition, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }
}
