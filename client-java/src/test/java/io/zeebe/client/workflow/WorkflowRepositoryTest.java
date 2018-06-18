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
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.util.StreamUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class WorkflowRepositoryTest {
  public ClientRule clientRule = new ClientRule();
  public StubBrokerRule brokerRule = new StubBrokerRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();

    registerGetResourceRequest();
    registerGetWorkflowRequest();
  }

  @Test
  public void shouldGetResourceByBpmnProcessId() {
    // when
    client
        .topicClient("test-topic")
        .workflowClient()
        .newResourceRequest()
        .bpmnProcessId("wf")
        .latestVersion()
        .send()
        .join();

    // then
    assertThat(brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW))
        .hasSize(1);

    final ControlMessageRequest request =
        brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW).get(0);
    assertThat(request.getData())
        .containsOnly(
            entry("topicName", "test-topic"),
            entry("bpmnProcessId", "wf"),
            entry("version", -1),
            entry("workflowKey", -1));
  }

  @Test
  public void shouldGetResourceByBpmnProcessIdAndVersion() {
    // when
    client
        .topicClient("test-topic")
        .workflowClient()
        .newResourceRequest()
        .bpmnProcessId("wf")
        .version(2)
        .send()
        .join();

    // then
    assertThat(brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW))
        .hasSize(1);

    final ControlMessageRequest request =
        brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW).get(0);
    assertThat(request.getData())
        .containsOnly(
            entry("topicName", "test-topic"),
            entry("bpmnProcessId", "wf"),
            entry("version", 2),
            entry("workflowKey", -1));
  }

  @Test
  public void shouldGetResourceByWorkflowKey() {
    // when
    client
        .topicClient("test-topic")
        .workflowClient()
        .newResourceRequest()
        .workflowKey(123L)
        .send()
        .join();

    // then
    assertThat(brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW))
        .hasSize(1);

    final ControlMessageRequest request =
        brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.GET_WORKFLOW).get(0);
    assertThat(request.getData())
        .containsOnly(
            entry("topicName", "test-topic"), entry("version", -1), entry("workflowKey", 123));
  }

  @Test
  public void shouldGetResourceResponse() throws Exception {
    // when
    final WorkflowResource workflowResource =
        client
            .topicClient("test-topic")
            .workflowClient()
            .newResourceRequest()
            .bpmnProcessId("wf")
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowResource.getBpmnProcessId()).isEqualTo("wf");
    assertThat(workflowResource.getVersion()).isEqualTo(1);
    assertThat(workflowResource.getWorkflowKey()).isEqualTo(123L);
    assertThat(workflowResource.getResourceName()).isEqualTo("wf.bpmn");
    assertThat(workflowResource.getBpmnXml()).isEqualTo("xml");
    assertThat(StreamUtil.read(workflowResource.getBpmnXmlAsStream()))
        .isEqualTo(workflowResource.getBpmnXml().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldGetWorkflowsByBpmnProcessId() {
    // when
    client
        .topicClient("test-topic")
        .workflowClient()
        .newWorkflowRequest()
        .bpmnProcessId("wf1")
        .send()
        .join();

    // then
    assertThat(
            brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.LIST_WORKFLOWS))
        .hasSize(1);

    final ControlMessageRequest request =
        brokerRule
            .getReceivedControlMessageRequestsByType(ControlMessageType.LIST_WORKFLOWS)
            .get(0);
    assertThat(request.getData())
        .containsOnly(entry("topicName", "test-topic"), entry("bpmnProcessId", "wf1"));
  }

  @Test
  public void shouldGetAllWorkflows() {
    // when
    client.topicClient("test-topic").workflowClient().newWorkflowRequest().send().join();

    // then
    assertThat(
            brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.LIST_WORKFLOWS))
        .hasSize(1);

    final ControlMessageRequest request =
        brokerRule
            .getReceivedControlMessageRequestsByType(ControlMessageType.LIST_WORKFLOWS)
            .get(0);
    assertThat(request.getData()).containsOnly(entry("topicName", "test-topic"));
  }

  @Test
  public void shouldGetWorkflowResponse() {
    // when
    final Workflows workflows =
        client.topicClient("test-topic").workflowClient().newWorkflowRequest().send().join();

    // then
    assertThat(
            brokerRule.getReceivedControlMessageRequestsByType(ControlMessageType.LIST_WORKFLOWS))
        .hasSize(1);

    assertThat(workflows.getWorkflows()).hasSize(2);
    assertThat(workflows.getWorkflows())
        .extracting(Workflow::getBpmnProcessId)
        .contains("wf1", "wf1");
    assertThat(workflows.getWorkflows()).extracting(Workflow::getVersion).contains(1, 2);
    assertThat(workflows.getWorkflows()).extracting(Workflow::getWorkflowKey).contains(123L, 456L);
    assertThat(workflows.getWorkflows())
        .extracting(Workflow::getResourceName)
        .contains("wf1.bpmn", "wf2.bpmn");
  }

  private void registerGetResourceRequest() {
    brokerRule
        .onControlMessageRequest(r -> r.messageType() == ControlMessageType.GET_WORKFLOW)
        .respondWith()
        .data()
        .put("bpmnProcessId", "wf")
        .put("version", 1)
        .put("workflowKey", 123L)
        .put("resourceName", "wf.bpmn")
        .put("bpmnXml", "xml")
        .done()
        .register();
  }

  private void registerGetWorkflowRequest() {
    final Map<String, Object> workflow1 = new HashMap<>();
    workflow1.put("bpmnProcessId", "wf1");
    workflow1.put("version", 1);
    workflow1.put("workflowKey", 123L);
    workflow1.put("resourceName", "wf1.bpmn");

    final Map<String, Object> workflow2 = new HashMap<>();
    workflow2.put("bpmnProcessId", "wf1");
    workflow2.put("version", 2);
    workflow2.put("workflowKey", 456L);
    workflow2.put("resourceName", "wf2.bpmn");

    brokerRule
        .onControlMessageRequest(r -> r.messageType() == ControlMessageType.LIST_WORKFLOWS)
        .respondWith()
        .data()
        .put("workflows", Arrays.asList(workflow1, workflow2))
        .done()
        .register();
  }
}
