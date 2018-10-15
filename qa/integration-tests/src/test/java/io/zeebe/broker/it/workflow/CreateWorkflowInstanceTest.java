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

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.model.bpmn.Bpmn;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  private DeploymentEvent firstDeployment;

  @Before
  public void deployProcess() {
    firstDeployment =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(
                Bpmn.createExecutableProcess("anId").startEvent().endEvent().done(),
                "workflow.bpmn")
            .send()
            .join();

    final DeploymentEvent secondDeployment =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(
                Bpmn.createExecutableProcess("anId").startEvent().endEvent().done(),
                "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(secondDeployment.getKey());
  }

  @Test
  public void shouldCreateBpmnProcessById() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("anId")
            .latestVersion()
            .send()
            .join();

    // then instance of latest of workflow version is created
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
    assertThat(workflowInstance.getVersion()).isEqualTo(2);
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldCreateBpmnProcessByIdAndVersion() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("anId")
            .version(1)
            .send()
            .join();

    // then instance is created of first workflow version
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
    assertThat(workflowInstance.getVersion()).isEqualTo(1);
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldCreateBpmnProcessByKey() {
    final long workflowKey = firstDeployment.getWorkflows().get(0).getWorkflowKey();

    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .workflowKey(workflowKey)
            .send()
            .join();

    // then
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
    assertThat(workflowInstance.getVersion()).isEqualTo(1);
    assertThat(workflowInstance.getWorkflowKey()).isEqualTo(workflowKey);

    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldCreateWithPayload() {
    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("anId")
        .latestVersion()
        .payload("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated(
        workflowInstance -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(workflowInstance.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCreateWithoutPayload() {
    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("anId")
        .latestVersion()
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated(
        workflowInstance -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{}");
          assertThat(workflowInstance.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCreateWithNullPayload() {
    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("anId")
        .latestVersion()
        .payload("null")
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated(
        workflowInstance -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{}");
          assertThat(workflowInstance.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldThrowExceptionOnCompleteJobWithInvalidPayload() {
    // given

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                clientRule
                    .getWorkflowClient()
                    .newCreateInstanceCommand()
                    .bpmnProcessId("anId")
                    .latestVersion()
                    .payload("[]")
                    .send()
                    .join());

    // then
    assertThat(throwable).isInstanceOf(ClientException.class);
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldCreateWithPayloadAsMap() {
    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("anId")
        .latestVersion()
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated(
        workflowInstance -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(workflowInstance.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCreateWithPayloadAsObject() {
    final PayloadObject payload = new PayloadObject();
    payload.foo = "bar";

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("anId")
        .latestVersion()
        .payload(payload)
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated(
        workflowInstance -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(workflowInstance.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldRejectCreateBpmnProcessByIllegalId() {
    // expected
    exception.expect(ClientException.class);
    exception.expectMessage("Command (CREATE) was rejected");

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("illegal")
        .latestVersion()
        .send()
        .join();
  }

  @Test
  public void shouldRejectCreateBpmnProcessByIllegalKey() {
    // expected
    exception.expect(ClientException.class);
    exception.expectMessage("Command (CREATE) was rejected");

    // when
    clientRule.getWorkflowClient().newCreateInstanceCommand().workflowKey(99L).send().join();
  }

  public static class PayloadObject {
    public String foo;
  }
}
