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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.response.EvaluateConditionalResponse;
import io.camunda.client.util.ClientTest;
import io.camunda.client.util.JsonUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateConditionalRequest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class EvaluateConditionalTest extends ClientTest {

  @Test
  public void shouldEvaluateConditionalWithoutProcessDefinitionKey() {
    // given
    gatewayService.onEvaluateConditionalRequest(2251799813685249L, 2251799813685250L);

    // when
    final EvaluateConditionalResponse response =
        client.newEvaluateConditionalCommand().variables("{\"x\":100}").send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(request.hasProcessDefinitionKey()).isFalse();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).contains(entry("x", 100));
    assertThat(request.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);

    assertThat(response.getProcessInstances()).hasSize(1);
    assertThat(response.getProcessInstances().get(0).getProcessDefinitionKey())
        .isEqualTo(2251799813685249L);
    assertThat(response.getProcessInstances().get(0).getProcessInstanceKey())
        .isEqualTo(2251799813685250L);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldEvaluateConditionalWithProcessDefinitionKey() {
    // given
    final long processDefinitionKey = 12345L;
    gatewayService.onEvaluateConditionalRequest(processDefinitionKey, 67890L);

    // when
    final EvaluateConditionalResponse response =
        client
            .newEvaluateConditionalCommand()
            .variables("{\"x\":100}")
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(request.hasProcessDefinitionKey()).isTrue();
    assertThat(request.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(response.getProcessInstances()).hasSize(1);
    assertThat(response.getProcessInstances().get(0).getProcessDefinitionKey())
        .isEqualTo(processDefinitionKey);
  }

  @Test
  public void shouldEvaluateConditionalWithStringVariables() {
    // given
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client.newEvaluateConditionalCommand().variables("{\"foo\":\"bar\"}").send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateConditionalWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\":\"bar\",\"baz\":123}";
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(variables.getBytes());
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client.newEvaluateConditionalCommand().variables(byteArrayInputStream).send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables()))
        .containsOnly(entry("foo", "bar"), entry("baz", 123));
  }

  @Test
  public void shouldEvaluateConditionalWithMapVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client.newEvaluateConditionalCommand().variables(variables).send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateConditionalWithObjectVariables() {
    // given
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client.newEvaluateConditionalCommand().variables(new Variables()).send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateConditionalWithSingleStringVariable() {
    // given
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    final String key = "key";
    final String value = "value";
    client.newEvaluateConditionalCommand().variable(key, value).send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).containsOnly(entry(key, value));
  }

  @Test
  public void shouldEvaluateConditionalWithSingleNumericVariable() {
    // given
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    final String key = "count";
    final Integer value = 123;
    client.newEvaluateConditionalCommand().variable(key, value).send().join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(JsonUtil.fromJsonAsMap(request.getVariables())).containsOnly(entry(key, value));
  }

  @Test
  public void shouldEvaluateConditionalWithEmptyResponse() {
    // given
    gatewayService.onEvaluateConditionalRequest();

    // when
    final EvaluateConditionalResponse response =
        client.newEvaluateConditionalCommand().variables("{\"x\":100}").send().join();

    // then
    assertThat(response.getProcessInstances()).isEmpty();
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        EvaluateConditionalRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () -> client.newEvaluateConditionalCommand().variables("{\"x\":100}").send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client
        .newEvaluateConditionalCommand()
        .variables("{\"x\":100}")
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldAllowSpecifyingTenantId() {
    // given
    gatewayService.onEvaluateConditionalRequest(123L, 456L);

    // when
    client
        .newEvaluateConditionalCommand()
        .variables("{\"x\":100}")
        .tenantId("custom-tenant")
        .send()
        .join();

    // then
    final EvaluateConditionalRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo("custom-tenant");
  }

  public static class Variables {

    Variables() {}

    public String getFoo() {
      return "bar";
    }
  }
}
