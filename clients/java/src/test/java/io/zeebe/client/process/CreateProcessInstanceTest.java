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
package io.zeebe.client.process;

import static io.zeebe.client.api.command.CreateProcessInstanceCommandStep1.LATEST_VERSION;
import static io.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.ProcessInstanceEvent;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.junit.Test;

public final class CreateProcessInstanceTest extends ClientTest {

  @Test
  public void shouldCreateProcessInstanceByProcessInstanceKey() {
    // given
    gatewayService.onCreateProcessInstanceRequest(123, "testProcess", 12, 32);

    // when
    final ProcessInstanceEvent response =
        client.newCreateInstanceCommand().processDefinitionKey(123).send().join();

    // then
    assertThat(response.getProcessDefinitionKey()).isEqualTo(123);
    assertThat(response.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(response.getVersion()).isEqualTo(12);
    assertThat(response.getProcessInstanceKey()).isEqualTo(32);

    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getProcessDefinitionKey()).isEqualTo(123);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessId() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").latestVersion().send().join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getVersion()).isEqualTo(LATEST_VERSION);
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessIdAndVersion() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").version(123).send().join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateProcessInstanceWithStringVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables("{\"foo\": \"bar\"}")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client.newCreateInstanceCommand().processDefinitionKey(123).variables(inputStream).send().join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithMapVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithObjectVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(new VariableDocument())
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldRaise() {
    // given
    gatewayService.errorOnRequest(
        CreateProcessInstanceRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(() -> client.newCreateInstanceCommand().processDefinitionKey(123).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newCreateInstanceCommand().processDefinitionKey(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  public static class VariableDocument {

    private final String foo = "bar";

    VariableDocument() {}

    public String getFoo() {
      return foo;
    }
  }
}
