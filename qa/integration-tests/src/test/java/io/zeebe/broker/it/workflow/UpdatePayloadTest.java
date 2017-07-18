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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskType;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.time.Duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest
{
    private static final String PAYLOAD = "{\"foo\": \"bar\"}";

    private static final ZeebeModelInstance WORKFLOW = wrap(Bpmn.createExecutableProcess("process")
                                                                .startEvent("start")
                                                                .serviceTask("task-1")
                                                                .serviceTask("task-2")
                                                                .endEvent("end")
                                                                .done())
                .taskDefinition("task-1", "task-1", 3)
                .taskDefinition("task-2", "task-2", 3)
                .ioMapping("task-1")
                    .output("$.result", "$.result")
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
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();
    }

    @Test
    public void shouldUpdatePayloadWhenActivityIsActivated()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_ACTIVATED")));

        final long activtyInstanceKey = eventRecorder
                .getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_ACTIVATED"))
                .getMetadata()
                .getEventKey();

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .activityInstanceKey(activtyInstanceKey)
            .payload(PAYLOAD)
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("PAYLOAD_UPDATED")));

        clientRule.taskTopic().newTaskSubscription()
            .taskType("task-1")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"result\": \"ok\"}");
                task.complete();
            })
            .open();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskType("task-2")));

        final TaskEvent task2 = eventRecorder.getTaskEvents(taskType("task-2")).get(0).getEvent();
        assertThat(task2.getPayload()).isEqualTo("{\"foo\":\"bar\",\"result\":\"ok\"}");
    }

    @Test
    public void shouldFailUpdatePayloadIfActivityIsCompleted()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("task-1")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"result\": \"done\"}");
                task.complete();
            })
            .open();

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED")));

        final long activityInstanceKey = eventRecorder
                .getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED"))
                .getMetadata()
                .getEventKey();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to update payload");

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .activityInstanceKey(activityInstanceKey)
            .payload(PAYLOAD)
            .execute();

    }

}
