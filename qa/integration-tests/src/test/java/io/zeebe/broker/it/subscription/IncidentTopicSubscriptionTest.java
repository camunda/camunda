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
package io.zeebe.broker.it.subscription;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.IncidentEvent;
import io.zeebe.client.event.IncidentEventHandler;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.test.util.TestUtil;

public class IncidentTopicSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();

        final BpmnModelInstance workflow =
                wrap(Bpmn.createExecutableProcess("process")
                     .startEvent("start")
                     .serviceTask("task")
                     .endEvent("end")
                     .done())
                .taskDefinition("task", "test", 3)
                    .ioMapping("task")
                        .input("$.foo", "$.foo")
                        .done();

        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(workflow)
            .execute();
    }

    @Test
    public void shouldReceiveWorkflowIncidentEvents()
    {
        // given
        final WorkflowInstanceEvent workflowInstance = clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .incidentEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 2);

        final IncidentEvent event = handler.getEvent(1);
        assertThat(event.getState()).isEqualTo("CREATED");
        assertThat(event.getErrorType()).isEqualTo("IO_MAPPING_ERROR");
        assertThat(event.getErrorMessage()).isEqualTo("No data found for query $.foo.");
        assertThat(event.getBpmnProcessId()).isEqualTo("process");
        assertThat(event.getWorkflowInstanceKey()).isEqualTo(workflowInstance.getWorkflowInstanceKey());
        assertThat(event.getActivityId()).isEqualTo("task");
        assertThat(event.getActivityInstanceKey()).isGreaterThan(0);
        assertThat(event.getTaskKey()).isNull();
    }

    @Test
    public void shouldReceiveTaskIncidentEvents()
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "test").execute();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .taskType("test")
            .handler((c, t) ->
            {
                throw new RuntimeException("expected failure");
            })
            .open();

        final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .incidentEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 2);

        final IncidentEvent event = handler.getEvent(1);
        assertThat(event.getState()).isEqualTo("CREATED");
        assertThat(event.getErrorType()).isEqualTo("TASK_NO_RETRIES");
        assertThat(event.getErrorMessage()).isEqualTo("No more retries left.");
        assertThat(event.getBpmnProcessId()).isNull();
        assertThat(event.getWorkflowInstanceKey()).isNull();
        assertThat(event.getActivityId()).isNull();
        assertThat(event.getActivityInstanceKey()).isNull();
        assertThat(event.getTaskKey()).isEqualTo(task.getMetadata().getKey());
    }

    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(handler)
            .name("sub-2")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEventsOfType(TopicEventType.INCIDENT) >= 2);
    }

    protected static class RecordingIncidentEventHandler implements IncidentEventHandler
    {
        protected List<IncidentEvent> events = new ArrayList<>();

        @Override
        public void handle(IncidentEvent event) throws Exception
        {
            this.events.add(event);
        }

        public IncidentEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
