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
package io.camunda.client.process.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class CreateProcessInstanceWithResultRestTest extends ClientRestTest {

  private static final ProcessInstanceResult DUMMY_RESPONSE =
      Instancio.create(ProcessInstanceResult.class)
          .processInstanceKey("1")
          .parentProcessInstanceKey("2")
          .parentElementInstanceKey("3")
          .processDefinitionKey("4");

  @Test
  public void shouldCreateProcessInstanceByProcessInstanceKey() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .withResult()
        .requestTimeout(Duration.ofSeconds(123))
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariables()).isEmpty();
    assertThat(request.getProcessDefinitionKey()).isEqualTo("123");
    assertThat(request.getRequestTimeout()).isEqualTo(123000L);
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessIdAndVersion() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .version(123)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo("testProcess");
    assertThat(request.getProcessDefinitionVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateProcessInstanceWithStringVariables() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables("{\"foo\": \"bar\"}")
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithSingleVariable() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    final String key = "key";
    final String value = "value";
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variable(key, value)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("test")
        .latestVersion()
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByLatestVersionOfProcessId() {
    // given
    final String bpmnProcessId = "testProcess";
    final String tenantId = "test-tenant";
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tenantId(tenantId)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessIdAndVersion() {
    // given
    final String bpmnProcessId = "testProcess";
    final int version = 3;
    final String tenantId = "test-tenant";
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .version(version)
        .tenantId(tenantId)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessDefinitionKey() {
    // given
    final Long processDefinitionKey = 1L;
    final String tenantId = "test-tenant";
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tenantId(tenantId)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowTags() {
    // given
    final Long processDefinitionKey = 1L;
    final Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2"));
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tags(tags)
        .withResult()
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTags()).isEqualTo(tags);
  }

  private static final class VariablesPojo {
    String key;

    public String getKey() {
      return key;
    }

    public CreateProcessInstanceWithResultRestTest.VariablesPojo setKey(final String key) {
      this.key = key;
      return this;
    }
  }
}
