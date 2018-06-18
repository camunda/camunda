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
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.TestUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceTopicSubscriptionTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  protected TopicClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient().topicClient();

    final WorkflowDefinition workflow =
        Bpmn.createExecutableWorkflow("process").startEvent("a").endEvent("b").done();

    client
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflow, "workflow.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldReceiveWorkflowInstanceEvents() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .payload("{\"foo\":123}")
            .send()
            .join();

    final RecordingWorkflowEventHandler handler = new RecordingWorkflowEventHandler();

    // when
    client
        .newSubscription()
        .name("test")
        .workflowInstanceEventHandler(handler)
        .startAtHeadOfTopic()
        .open();

    // then
    TestUtil.waitUntil(() -> handler.numRecords() >= 2);

    final WorkflowInstanceEvent event = handler.getEvent(1);
    assertThat(event.getState()).isEqualTo(WorkflowInstanceState.START_EVENT_OCCURRED);
    assertThat(event.getBpmnProcessId()).isEqualTo("process");
    assertThat(event.getVersion()).isEqualTo(1);
    assertThat(event.getWorkflowInstanceKey()).isEqualTo(workflowInstance.getWorkflowInstanceKey());
    assertThat(event.getActivityId()).isEqualTo("a");
    assertThat(event.getPayload()).isEqualTo("{\"foo\":123}");
  }

  @Test
  public void shouldInvokeDefaultHandler() throws IOException {
    client
        .workflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload("{\"foo\":123}")
        .send()
        .join();

    final RecordingEventHandler handler = new RecordingEventHandler();

    // when no POJO handler is registered
    client.newSubscription().name("sub-2").recordHandler(handler).startAtHeadOfTopic().open();

    // then
    TestUtil.waitUntil(() -> handler.numRecordsOfType(ValueType.WORKFLOW_INSTANCE) >= 3);
  }

  protected static class RecordingWorkflowEventHandler implements WorkflowInstanceEventHandler {
    protected List<WorkflowInstanceEvent> events = new ArrayList<>();

    @Override
    public void onWorkflowInstanceEvent(WorkflowInstanceEvent event) throws Exception {
      this.events.add(event);
    }

    public WorkflowInstanceEvent getEvent(int index) {
      return events.get(index);
    }

    public int numRecords() {
      return events.size();
    }
  }
}
