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
import io.zeebe.client.api.events.WorkflowInstanceEvent.WorkflowInstanceState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest
{
    private static final String PAYLOAD = "{\"foo\": \"bar\"}";

    private static final WorkflowDefinition WORKFLOW = Bpmn
            .createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task-1", t -> t.taskType("task-1")
                         .output("$.result", "$.result"))
            .endEvent("end")
            .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init()
    {
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();
    }

    @Test
    public void shouldUpdatePayloadWhenActivityIsActivated()
    {
        // given
        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

        final WorkflowInstanceEvent activtyInstance = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

        // when
        clientRule.getWorkflowClient()
            .newUpdatePayloadCommand(activtyInstance)
            .payload(PAYLOAD)
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("task-1")
            .handler((client, job) -> client.newCompleteCommand(job).payload("{\"result\": \"ok\"}").send())
            .open();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));

        final WorkflowInstanceEvent wfEvent = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED);
        assertThat(wfEvent.getPayload()).isEqualTo("{\"foo\":\"bar\",\"result\":\"ok\"}");
    }

    @Test
    public void shouldFailUpdatePayloadIfWorkflowInstanceIsCompleted()
    {
        // given
        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("task-1")
            .handler((client, job) -> client.newCompleteCommand(job).payload("{\"result\": \"done\"}").send())
            .open();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED));

        final WorkflowInstanceEvent activityInstance = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command for event with key " + activityInstance.getMetadata().getKey() +
                " was rejected by broker (UPDATE_PAYLOAD)");

        // when
        clientRule.getWorkflowClient()
            .newUpdatePayloadCommand(activityInstance)
            .payload(PAYLOAD)
            .send()
            .join();
    }

}
