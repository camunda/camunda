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
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.time.Duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.Task;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest
{
    private static final ZeebeModelInstance WORKFLOW = wrap(Bpmn.createExecutableProcess("process")
                                                                .startEvent("start")
                                                                .serviceTask("task")
                                                                .endEvent("end")
                                                                .done())
                .taskDefinition("task", "test", 3);

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
    public void shouldCancelWorkflowInstance()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

    @Test
    public void shouldFailCancelNotExistingWorkflowInstance()
    {
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to cancel workflow instance with key '3'.");

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(3L)
            .execute();
    }

    @Test
    public void shouldFailToCompleteTaskAfterCancel()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        final PollableTaskSubscription taskSubscription = clientRule.taskTopic().newPollableTaskSubscription()
            .taskType("test")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(1))
            .open();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("LOCKED")));

        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // when
        taskSubscription.poll(Task::complete);

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETE_REJECTED")));

        assertThat(eventRecorder.hasTaskEvent(taskEvent("CANCELED")));
        assertThat(eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

    @Test
    public void shouldFailToLockTaskAfterCancel()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        final PollableTaskSubscription taskSubscription = clientRule.taskTopic().newPollableTaskSubscription()
                .taskType("test")
                .lockOwner("owner")
                .lockTime(Duration.ofMinutes(1))
                .open();

        // when
        final int completedTasks = taskSubscription.poll(Task::complete);

        // then
        assertThat(completedTasks).isEqualTo(0);

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("LOCK_REJECTED")));

        assertThat(eventRecorder.hasTaskEvent(taskEvent("CANCELED")));
        assertThat(eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

}
