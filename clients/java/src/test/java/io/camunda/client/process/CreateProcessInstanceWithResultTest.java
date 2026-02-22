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

import static io.camunda.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class CreateProcessInstanceWithResultTest extends ClientTest {

  @Test
  public void shouldCreateProcessInstanceByProcessInstanceKey() {
    // given
    final String variables = "{\"key\": \"val\"}";
    gatewayService.onCreateProcessInstanceWithResultRequest(123, "testProcess", 12, 32, variables);

    // when
    final ProcessInstanceResult response =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(123)
            .withResult()
            .requestTimeout(Duration.ofSeconds(123))
            .send()
            .join();

    // then
    assertThat(response.getProcessDefinitionKey()).isEqualTo(123);
    assertThat(response.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(response.getVersion()).isEqualTo(12);
    assertThat(response.getProcessInstanceKey()).isEqualTo(32);
    assertThat(response.getVariablesAsMap()).containsExactly(entry("key", "val"));
    assertThat(response.getVariables()).isEqualTo(variables);
    final VariablesPojo result = response.getVariablesAsType(VariablesPojo.class);
    assertThat(result.getKey()).isEqualTo("val");
    assertThat(response.getTenantId()).isEqualTo("");

    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getProcessDefinitionKey()).isEqualTo(123);
    assertThat(request.getRequestTimeout()).isEqualTo(Duration.ofSeconds(123).toMillis());
    assertThat(request.getFetchVariablesList()).isEmpty();
  }

  @Test
  public void shouldBeAbleToSpecifyFetchVariables() {
    // given
    final String variables = "{\"key\": \"val\"}";
    gatewayService.onCreateProcessInstanceWithResultRequest(123, "testProcess", 12, 32, variables);

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .withResult()
        .fetchVariables("x")
        .requestTimeout(Duration.ofSeconds(123))
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getProcessDefinitionKey()).isEqualTo(123);
    assertThat(request.getRequestTimeout()).isEqualTo(Duration.ofSeconds(123).toMillis());
    assertThat(request.getFetchVariablesList()).containsExactly("x");
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessIdAndVersion() {
    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .version(123)
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getRequest().getVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateProcessInstanceWithStringVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables("{\"foo\": \"bar\"}")
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getRequest().getVariables()))
        .containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithSingleVariable() {
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
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getRequest().getVariables())).containsOnly(entry(key, value));
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("test")
        .latestVersion()
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(piRequest.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
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
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(piRequest.getTenantId()).isEqualTo(tenantId);
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
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(piRequest.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessDefinitionKey() {
    // given
    final Long processDefinitionKey = 1L;
    final String tenantId = "test-tenant";

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tenantId(tenantId)
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(piRequest.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAddTags() {
    // given
    final Long processDefinitionKey = 1L;
    final Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2"));

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tags(tags)
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(new HashSet(piRequest.getTagsList())).isEqualTo(tags);
  }

  @Test
  public void shouldAddBusinessId() {
    // given
    final Long processDefinitionKey = 1L;
    final String businessId = "order-12345";

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .businessId(businessId)
        .withResult()
        .send()
        .join();

    // then
    final CreateProcessInstanceWithResultRequest request = gatewayService.getLastRequest();
    final CreateProcessInstanceRequest piRequest = request.getRequest();
    assertThat(piRequest.getBusinessId()).isEqualTo(businessId);
  }

  private static final class VariablesPojo {
    String key;

    public String getKey() {
      return key;
    }

    public VariablesPojo setKey(final String key) {
      this.key = key;
      return this;
    }
  }
}
