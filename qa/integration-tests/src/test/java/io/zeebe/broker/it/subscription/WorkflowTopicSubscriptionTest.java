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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.event.*;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowTopicSubscriptionTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent("a")
            .endEvent("b")
            .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Test
    public void shouldReceiveWorkflowEvent()
    {
        // given
        final DeploymentEvent deploymentResult = clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        final RecordingWorkflowEventHandler handler = new RecordingWorkflowEventHandler();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .workflowEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 2);

        assertThat(handler.getEvent(0).getState()).isEqualTo("CREATE");
        assertThat(handler.getEvent(1).getState()).isEqualTo("CREATED");

        for (WorkflowEvent event : handler.events)
        {
            assertThat(event.getBpmnProcessId()).isEqualTo("process");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getDeploymentKey()).isEqualTo(deploymentResult.getMetadata().getKey());
            assertThat(event.getBpmnXml()).isEqualTo(Bpmn.convertToString(WORKFLOW));
        }
    }

    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
                .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(handler)
            .name("sub-2")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEventsOfType(TopicEventType.WORKFLOW) >= 2);
    }

    protected static class RecordingWorkflowEventHandler implements WorkflowEventHandler
    {
        protected List<WorkflowEvent> events = new ArrayList<>();

        @Override
        public void handle(WorkflowEvent event) throws Exception
        {
            this.events.add(event);
        }

        public WorkflowEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
