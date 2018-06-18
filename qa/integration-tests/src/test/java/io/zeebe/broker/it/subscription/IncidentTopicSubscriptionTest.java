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

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.events.IncidentEvent;
import io.zeebe.client.api.events.IncidentState;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.client.api.subscription.IncidentEventHandler;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.TestUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTopicSubscriptionTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  protected TopicClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient().topicClient();

    final WorkflowDefinition workflow =
        Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("test").input("$.foo", "$.foo"))
            .endEvent("end")
            .done();

    client
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflow, "workflow.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldReceiveWorkflowIncidentEvents() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

    // when
    client.newSubscription().name("test").incidentEventHandler(handler).startAtHeadOfTopic().open();

    // then
    TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 1);

    final IncidentEvent event = handler.getEvent(0);
    assertThat(event.getState()).isEqualTo(IncidentState.CREATED);
    assertThat(event.getErrorType()).isEqualTo("IO_MAPPING_ERROR");
    assertThat(event.getErrorMessage()).isEqualTo("No data found for query $.foo.");
    assertThat(event.getBpmnProcessId()).isEqualTo("process");
    assertThat(event.getWorkflowInstanceKey()).isEqualTo(workflowInstance.getWorkflowInstanceKey());
    assertThat(event.getActivityId()).isEqualTo("task");
    assertThat(event.getActivityInstanceKey()).isGreaterThan(0);
    assertThat(event.getJobKey()).isNull();
  }

  @Test
  public void shouldReceiveTaskIncidentEvents() {
    // given
    final JobEvent job = client.jobClient().newCreateCommand().jobType("test").send().join();

    client
        .jobClient()
        .newWorker()
        .jobType("test")
        .handler(
            (c, t) -> {
              throw new RuntimeException("expected failure");
            })
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

    // when
    client.newSubscription().name("test").incidentEventHandler(handler).startAtHeadOfTopic().open();

    // then
    TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 1);

    final IncidentEvent event = handler.getEvent(0);
    assertThat(event.getState()).isEqualTo(IncidentState.CREATED);
    assertThat(event.getErrorType()).isEqualTo("JOB_NO_RETRIES");
    assertThat(event.getErrorMessage()).isEqualTo("No more retries left.");
    assertThat(event.getBpmnProcessId()).isNull();
    assertThat(event.getWorkflowInstanceKey()).isNull();
    assertThat(event.getActivityId()).isNull();
    assertThat(event.getActivityInstanceKey()).isNull();
    assertThat(event.getJobKey()).isEqualTo(job.getKey());
  }

  @Test
  public void shouldInvokeDefaultHandler() throws IOException {
    // given
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    final RecordingEventHandler handler = new RecordingEventHandler();

    // when no POJO handler is registered
    client.newSubscription().name("sub-2").recordHandler(handler).startAtHeadOfTopic().open();

    // then
    TestUtil.waitUntil(() -> handler.numRecordsOfType(ValueType.INCIDENT) >= 2);
  }

  protected static class RecordingIncidentEventHandler implements IncidentEventHandler {
    protected List<IncidentEvent> events = new ArrayList<>();

    @Override
    public void onIncidentEvent(IncidentEvent event) throws Exception {
      this.events.add(event);
    }

    public IncidentEvent getEvent(int index) {
      return events.get(index);
    }

    public int numRecordedEvents() {
      return events.size();
    }
  }
}
