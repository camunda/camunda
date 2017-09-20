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

import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.TasksClient;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.*;
import org.junit.rules.RuleChain;

public class ExclusiveGatewayTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    private WorkflowsClient workflowClient;
    private TasksClient taskClient;

    @Before
    public void init()
    {
        workflowClient = clientRule.workflows();
        taskClient = clientRule.tasks();
    }

    @Test
    public void shouldEvaluateConditionOnFlow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlow(s -> s.condition("$.foo < 5"))
                .endEvent("a")
            .sequenceFlow(s -> s.defaultFlow())
                .endEvent("b")
                .done();

        workflowClient.deploy(clientRule.getDefaultTopic())
            .workflowModel(workflowDefinition)
            .execute();

        // when
        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("workflow")
                .payload("{\"foo\":3}")
                .execute();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));

        final WorkflowInstanceEvent endEvent = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("END_EVENT_OCCURRED"));
        assertThat(endEvent.getActivityId()).isEqualTo("a");
    }

    @Test
    public void shouldTakeDefaultFlow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlow(s -> s.condition("$.foo < 5"))
                .endEvent("a")
            .sequenceFlow(s -> s.defaultFlow())
                .endEvent("b")
                .done();

        workflowClient.deploy(clientRule.getDefaultTopic())
            .workflowModel(workflowDefinition)
            .execute();

        // when
        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("workflow")
                .payload("{\"foo\":7}")
                .execute();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));

        final WorkflowInstanceEvent endEvent = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("END_EVENT_OCCURRED"));
        assertThat(endEvent.getActivityId()).isEqualTo("b");
    }

    @Test
    public void shouldExecuteWorkflowWithLoop()
    {
        // given
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
                .startEvent()
                .serviceTask("inc", t -> t.taskType("inc"))
                .exclusiveGateway()
                .sequenceFlow(s -> s.condition("$.count > 5"))
                    .endEvent()
                .sequenceFlow("back", s -> s.defaultFlow())
                    .joinWith("inc")
                    .done();

        workflowClient.deploy(clientRule.getDefaultTopic())
            .workflowModel(workflowDefinition)
            .execute();

        // when
        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("workflow")
                .payload("{\"count\":0}")
                .execute();

        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .lockTime(Duration.ofSeconds(5))
            .lockOwner("test")
            .taskType("inc")
            .handler((c, task) ->
            {
                final String payload = task.getPayload();
                final int i = payload.indexOf(":");
                final int count = Integer.valueOf(payload.substring(i + 1, i + 2));

                c.complete(task).payload("{\"count\":" + (count + 1) + "}").execute();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));

        final WorkflowInstanceEvent event = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED"));
        assertThat(event.getPayload()).isEqualTo("{\"count\":6}");
    }

}
