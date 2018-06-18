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
package io.zeebe.broker.it.incident;

import static io.zeebe.broker.it.util.TopicEventRecorder.state;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.IncidentState;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest {
  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.taskType("test").input("$.foo", "$.foo"))
          .done();

  private static final String PAYLOAD = "{\"foo\": \"bar\"}";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  private WorkflowClient workflowClient;
  private JobClient jobClient;

  @Before
  public void setUp() {
    workflowClient = clientRule.getClient().topicClient().workflowClient();
    jobClient = clientRule.getClient().topicClient().jobClient();
  }

  @Test
  public void shouldCreateAndResolveInputMappingIncident() {
    // given
    workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.CREATED));

    final WorkflowInstanceEvent activityInstanceEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_READY);

    // when
    workflowClient.newUpdatePayloadCommand(activityInstanceEvent).payload(PAYLOAD).send().join();

    // then
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.CREATED)));
    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.RESOLVED));
  }

  @Test
  public void shouldDeleteIncidentWhenWorkflowInstanceIsCanceled() {
    // given
    workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    final WorkflowInstanceEvent workflowInstance =
        workflowClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.CREATED));

    // when
    workflowClient.newCancelInstanceCommand(workflowInstance).send().join();

    // then
    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.DELETED));
  }

  @Test
  public void shouldCreateAndResolveJobIncident() {
    // given a workflow instance with an open job
    workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload(PAYLOAD)
        .send()
        .join();

    // when the job fails until it has no more retries left
    final ControllableJobHandler jobHandler = new ControllableJobHandler();
    jobHandler.failJob = true;

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("test")
        .handler(jobHandler)
        .name("owner")
        .timeout(Duration.ofMinutes(5))
        .open();

    // then an incident is created
    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.CREATED));

    // when the job retries are increased
    jobHandler.failJob = false;

    final JobEvent job = jobHandler.job;

    jobClient.newUpdateRetriesCommand(job).retries(3).send().join();

    // then the incident is deleted
    waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.DELETED));
  }

  private static final class ControllableJobHandler implements JobHandler {
    boolean failJob = false;
    JobEvent job;

    @Override
    public void handle(JobClient client, JobEvent job) {
      this.job = job;

      if (failJob) {
        throw new RuntimeException("expected failure");
      } else {
        client.newCompleteCommand(job).withoutPayload().send().join();
      }
    }
  }
}
