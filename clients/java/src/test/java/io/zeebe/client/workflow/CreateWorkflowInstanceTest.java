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

import static io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1.LATEST_VERSION;
import static io.zeebe.test.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.base.Charsets;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import org.junit.Test;

public class CreateWorkflowInstanceTest extends ClientTest {

  @Test
  public void shouldCreateWorkflowInstanceByWorkflowInstanceKey() {
    // given
    gatewayService.onCreateWorkflowInstanceRequest(123, "testProcess", 12, 32);

    // when
    final WorkflowInstanceEvent response =
        client.newCreateInstanceCommand().workflowKey(123).send().join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(123);
    assertThat(response.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(response.getVersion()).isEqualTo(12);
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(32);

    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorkflowKey()).isEqualTo(123);
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessId() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").latestVersion().send().join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getVersion()).isEqualTo(LATEST_VERSION);
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessIdAndVersion() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").version(123).send().join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateWorkflowInstanceWithStringVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .workflowKey(123)
        .variables("{\"foo\": \"bar\"}")
        .send()
        .join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream = new ByteArrayInputStream(variables.getBytes(Charsets.UTF_8));

    // when
    client.newCreateInstanceCommand().workflowKey(123).variables(inputStream).send().join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithMapVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .workflowKey(123)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateWorkflowInstanceWithObjectVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .workflowKey(123)
        .variables(new VariableDocument())
        .send()
        .join();

    // then
    final CreateWorkflowInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldRaise() {
    // given
    gatewayService.errorOnRequest(
        CreateWorkflowInstanceRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(() -> client.newCreateInstanceCommand().workflowKey(123).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  public static class VariableDocument {

    private final String foo = "bar";

    VariableDocument() {}

    public String getFoo() {
      return foo;
    }
  }
}
