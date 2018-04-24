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

import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.impl.job.PollableTaskSubscription;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn
            .createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("test"))
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
    public void shouldCancelWorkflowInstance()
    {
        // given
        final WorkflowInstanceEvent workflowInstance = clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        // when
        clientRule.workflows().cancel(workflowInstance).execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

    @Test
    public void shouldFailToCompleteTaskAfterCancel()
    {
        // given
        final WorkflowInstanceEvent workflowInstance = clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        final PollableTaskSubscription taskSubscription = clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopic())
            .taskType("test")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(1))
            .open();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("LOCKED")));

        clientRule.workflows().cancel(workflowInstance).execute();

        // when
        taskSubscription.poll((c, t) -> c.complete(t).withoutPayload().execute());

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETE_REJECTED")));

        assertThat(eventRecorder.hasTaskEvent(taskEvent("CANCELED")));
        assertThat(eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

    @Test
    public void shouldFailToLockTaskAfterCancel()
    {
        // given
        final WorkflowInstanceEvent workflowInstance = clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        clientRule.workflows().cancel(workflowInstance).execute();

        final PollableTaskSubscription taskSubscription = clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopic())
                .taskType("test")
                .lockOwner("owner")
                .lockTime(Duration.ofMinutes(1))
                .open();

        // when
        final int completedTasks = taskSubscription.poll((c, t) -> c.complete(t).withoutPayload().execute());

        // then
        assertThat(completedTasks).isEqualTo(0);

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("LOCK_REJECTED")));

        assertThat(eventRecorder.hasTaskEvent(taskEvent("CANCELED")));
        assertThat(eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CANCELED")));
    }

}
