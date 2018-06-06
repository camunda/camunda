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

import io.zeebe.client.api.clients.WorkflowClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;

public class YamlWorkflowTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addResourceFromClasspath("workflows/simple-workflow.yaml")
            .send()
            .join();

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("yaml-workflow")
            .latestVersion()
            .send()
            .join();

        // then
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CREATED));
    }

    @Test
    public void shouldCompleteWorkflowInstanceWithTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addResourceFromClasspath("workflows/simple-workflow.yaml")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("yaml-workflow")
            .latestVersion()
            .send()
            .join();

        // when
        clientRule.getJobClient()
            .newWorker()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).withoutPayload().send())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    }

    @Test
    public void shouldGetTaskWithHeaders()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addResourceFromClasspath("workflows/workflow-with-headers.yaml")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow-headers")
            .latestVersion()
            .send()
            .join();

        // when
        final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

        clientRule.getJobClient()
            .newWorker()
            .jobType("foo")
            .handler(recordingJobHandler)
            .open();

        // then
        waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

        final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
        assertThat(jobEvent.getCustomHeaders())
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");
    }

    @Test
    public void shouldCompleteTaskWithPayload()
    {
        // given
        final WorkflowClient workflowClient = clientRule.getWorkflowClient();
        workflowClient
            .newDeployCommand()
            .addResourceFromClasspath("workflows/workflow-with-mappings.yaml")
            .send()
            .join();

        workflowClient
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow-mappings")
            .latestVersion()
            .payload("{\"foo\":1}")
            .send()
            .join();

        // when
        final RecordingJobHandler recordingTaskHandler = new RecordingJobHandler((client, job) -> client
            .newCompleteCommand(job)
            .payload("{\"result\":3}")
            .send());

        clientRule.getJobClient()
            .newWorker()
            .jobType("foo")
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledJobs().size() >= 1);

        final JobEvent jobEvent = recordingTaskHandler.getHandledJobs().get(0);
        assertThat(jobEvent.getPayload()).isEqualTo("{\"bar\":1}");

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED);
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":1,\"result\":3}");
    }

}
