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
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.WorkflowInstanceEvent;
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
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();
    }

    @Test
    public void shouldUpdatePayloadWhenActivityIsActivated()
    {
        // given
        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_ACTIVATED")));

        final WorkflowInstanceEvent activtyInstance = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_ACTIVATED"));

        // when
        clientRule.workflows().updatePayload(activtyInstance)
            .payload(PAYLOAD)
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("PAYLOAD_UPDATED")));

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("task-1")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) -> c.complete(t).payload("{\"result\": \"ok\"}").execute())
            .open();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));

        final WorkflowInstanceEvent wfEvent = eventRecorder.getWorkflowInstanceEvents(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")).get(0);
        assertThat(wfEvent.getPayload()).isEqualTo("{\"foo\":\"bar\",\"result\":\"ok\"}");
    }

    @Test
    public void shouldFailUpdatePayloadIfWorkflowInstanceIsCompleted()
    {
        // given
        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("task-1")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) -> c.complete(t).payload("{\"result\": \"done\"}").execute())
            .open();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED")));

        final WorkflowInstanceEvent activityInstance = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_ACTIVATED"));

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command for event with key " + activityInstance.getMetadata().getKey() +
                " was rejected by broker (UPDATE_PAYLOAD_REJECTED)");

        // when
        clientRule.workflows().updatePayload(activityInstance)
            .payload(PAYLOAD)
            .execute();

    }

}
