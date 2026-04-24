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
package io.camunda.client.process;

import static io.camunda.client.api.command.CreateProcessInstanceCommandStep1.LATEST_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.util.ClientTest;
import io.camunda.client.util.JsonUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceCreationStartInstruction;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
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
    assertThat(response.getTenantId()).isEqualTo("");
    assertThat(response.getBusinessId()).isNull();

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
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"));
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
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithSingleVariable() {
    // given
    final String key = "key";
    final String value = "value";

    // when
    client.newCreateInstanceCommand().processDefinitionKey(123).variable(key, value).send().join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry(key, value));
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
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"));
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
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"));
  }

  // Verifies repeated addVariable calls accumulate entries.
  @Test
  public void shouldCreateProcessInstanceWithMultipleAddVariableCalls() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariable("key1", "value1")
        .addVariable("key2", "value2")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key1", "value1"), entry("key2", "value2"));
  }

  // Verifies addVariables and addVariable can be mixed and still accumulate.
  @Test
  public void shouldAccumulateAddVariableWithAddVariablesMap() {
    // given
    final java.util.Map<String, Object> variables = new java.util.HashMap<>();
    variables.put("key1", "value1");

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariables(variables)
        .addVariable("key2", "value2")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key1", "value1"), entry("key2", "value2"));
  }

  // Verifies map-based reset works the same with addVariables as with addVariable.
  @Test
  public void shouldResetAccumulatedVariablesWhenCallingVariablesMapAndAddVariables() {
    // given
    final java.util.Map<String, Object> replacementVariables = new java.util.HashMap<>();
    replacementVariables.put("key2", "value2");
    final java.util.Map<String, Object> additionalVariables = new java.util.HashMap<>();
    additionalVariables.put("key3", "value3");

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariable("key1", "value1")
        .variables(replacementVariables)
        .addVariables(additionalVariables)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key2", "value2"), entry("key3", "value3"));
  }

  // Verifies single-variable reset clears prior additive state before continuing accumulation.
  @Test
  public void shouldResetAccumulatedVariablesWhenCallingVariable() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariable("key1", "value1")
        .variable("key2", "value2")
        .addVariable("key3", "value3")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key2", "value2"), entry("key3", "value3"));
  }

  // Verifies addVariables pre-state is cleared by string reset before continuing accumulation.
  @Test
  public void shouldSeedAccumulationWhenCallingVariablesStringAfterAddVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariables(Collections.singletonMap("key1", "value1"))
        .variables("{\"key2\":\"value2\"}")
        .addVariable("key3", "value3")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key2", "value2"), entry("key3", "value3"));
  }

  // Verifies addVariables pre-state is cleared by InputStream reset before continuing accumulation.
  @Test
  public void shouldSeedAccumulationWhenCallingVariablesInputStreamAfterAddVariables() {
    // given
    final String variables = "{\"key2\":\"value2\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariables(Collections.singletonMap("key1", "value1"))
        .variables(inputStream)
        .addVariable("key3", "value3")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key2", "value2"), entry("key3", "value3"));
  }

  // Verifies addVariables pre-state is cleared by object reset before continuing accumulation.
  @Test
  public void shouldSeedAccumulationWhenCallingVariablesObjectAfterAddVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariables(Collections.singletonMap("key1", "value1"))
        .variables(new VariableDocument())
        .addVariable("key3", "value3")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"), entry("key3", "value3"));
  }

  // Verifies string reset also composes correctly with addVariables after reset.
  @Test
  public void shouldSeedAccumulationWhenCallingVariablesStringAndAddVariablesAfterAddVariable() {
    // given
    final java.util.Map<String, Object> additionalVariables = new java.util.HashMap<>();
    additionalVariables.put("key3", "value3");

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .addVariable("key1", "value1")
        .variables("{\"key2\":\"value2\"}")
        .addVariables(additionalVariables)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    Assertions.assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("key2", "value2"), entry("key3", "value3"));
  }

  @Test
  public void shouldRaiseAnErrorIfRequestFails() {
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

  @Test
  public void shouldUseDefaultTenantId() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("test").latestVersion().send().join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByLatestVersionOfProcessId() {
    // given
    final String bpmnProcessId = "testProcess";
    final String tenantId = "test-tenant";

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tenantId(tenantId)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessIdAndVersion() {
    // given
    final String bpmnProcessId = "testProcess";
    final int version = 3;
    final String tenantId = "test-tenant";

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .version(version)
        .tenantId(tenantId)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessDefinitionKey() {
    // given
    final String customTenantId = "test-tenant";
    final Long processDefinitionKey = 1L;

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tenantId(customTenantId)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(customTenantId);
    assertThat(request.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  public void shouldAddSuspendRuntimeInstruction() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .terminateAfterElement("elementId_A")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getRuntimeInstructionsList())
        .hasSize(1)
        .allSatisfy(
            instruction ->
                assertThat(instruction.getTerminate())
                    .satisfies(
                        suspend ->
                            assertThat(suspend.getAfterElementId()).isEqualTo("elementId_A")));
  }

  @Test
  public void shouldAddMultipleSuspendRuntimeInstructions() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .terminateAfterElement("elementId_A")
        .terminateAfterElement("elementId_B")
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getRuntimeInstructionsList())
        .hasSize(2)
        .allSatisfy(
            instruction ->
                assertThat(instruction.getTerminate())
                    .satisfies(
                        suspend ->
                            assertThat(suspend.getAfterElementId())
                                .isIn("elementId_A", "elementId_B")));
  }

  @Test
  public void shouldCreateWithTags() {
    // given
    final Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2"));

    gatewayService.onCreateProcessInstanceRequest(123, "testProcess", 12, 32, tags);

    // when
    final ProcessInstanceEvent response =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(123)
            .tags("tag1", "tag2")
            .send()
            .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(new HashSet<>(request.getTagsList())).isEqualTo(tags);

    assertThat(response.getTags()).isEqualTo(tags);
  }

  @Test
  public void shouldCreateWithBusinessId() {
    // given
    final String businessId = "order-12345";

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .businessId(businessId)
        .send()
        .join();

    // then
    final CreateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getBusinessId()).isEqualTo(businessId);
  }

  public static class VariableDocument {

    VariableDocument() {}

    public String getFoo() {
      return "bar";
    }
  }
}
