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
package io.camunda.zeebe.client.process;

import static io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.LATEST_VERSION;
import static io.camunda.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceCreationStartInstruction;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class CreateProcessInstanceTest extends ClientTest {

  public static final String ELEMENT_ID_A = "elementId_A";
  public static final String ELEMENT_ID_B = "elementId_B";

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
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(inputStream)
        .send()
        .join();

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
    assertThatThrownBy(
            () -> client.newCreateInstanceCommand().processDefinitionKey(123).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldAddStartInstruction() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .startBeforeElement(ELEMENT_ID_A)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();

    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructionsList();
    assertThat(startInstructionList).hasSize(1);
    final ProcessInstanceCreationStartInstruction startInstructionA = startInstructionList.get(0);
    assertThat(startInstructionA.getElementId()).isEqualTo(ELEMENT_ID_A);
  }

  @Test
  public void shouldAddMultipleStartInstructions() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .startBeforeElement(ELEMENT_ID_A)
        .startBeforeElement(ELEMENT_ID_B)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();

    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructionsList();
    assertThat(startInstructionList).hasSize(2);
    final ProcessInstanceCreationStartInstruction startInstructionA = startInstructionList.get(0);
    assertThat(startInstructionA.getElementId()).isEqualTo(ELEMENT_ID_A);
    final ProcessInstanceCreationStartInstruction startInstructionB = startInstructionList.get(1);
    assertThat(startInstructionB.getElementId()).isEqualTo(ELEMENT_ID_B);
  }

  public static class VariableDocument {

    private final String foo = "bar";

    VariableDocument() {}

    public String getFoo() {
      return foo;
    }
  }
}
