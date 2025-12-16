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

import io.camunda.client.api.response.EvaluateConditionalResponse;
import io.camunda.client.protocol.rest.ConditionalEvaluationInstruction;
import io.camunda.client.protocol.rest.EvaluateConditionalResult;
import io.camunda.client.protocol.rest.ProcessInstanceReference;
import io.camunda.client.util.ClientRestTest;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EvaluateConditionalRestTest extends ClientRestTest {

  private static final EvaluateConditionalResult DUMMY_RESPONSE =
      new EvaluateConditionalResult()
          .addProcessInstancesItem(
              new ProcessInstanceReference()
                  .processDefinitionKey("2251799813685249")
                  .processInstanceKey("2251799813685250"));

  @Test
  public void shouldEvaluateConditionalWithStringVariables() {
    // given
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client.newEvaluateConditionalCommand().variables("{\"foo\":\"bar\"}").send().join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
    assertThat(request.getProcessDefinitionKey()).isNull();
  }

  @Test
  public void shouldEvaluateConditionalWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\":\"bar\",\"baz\":42}";
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(variables.getBytes());
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client.newEvaluateConditionalCommand().variables(byteArrayInputStream).send().join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"), entry("baz", 42));
  }

  @Test
  public void shouldEvaluateConditionalWithMapVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client.newEvaluateConditionalCommand().variables(variables).send().join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateConditionalWithObjectVariables() {
    // given
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client.newEvaluateConditionalCommand().variables(new Variables()).send().join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @ParameterizedTest
  @MethodSource("singleVariableTestCases")
  public void shouldEvaluateConditionalWithSingleVariable(final String key, final Object value) {
    // given
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client.newEvaluateConditionalCommand().variable(key, value).send().join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  private static Stream<Arguments> singleVariableTestCases() {
    return Stream.of(Arguments.of("key", "value"), Arguments.of("count", 123));
  }

  @Test
  public void shouldEvaluateConditionalWithProcessDefinitionKey() {
    // given
    final long processDefinitionKey = 12345L;
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client
        .newEvaluateConditionalCommand()
        .variables("{\"x\":100}")
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
  }

  @Test
  public void shouldEvaluateConditionalWithTenantId() {
    // given
    gatewayService.onEvaluateConditionalRequest(DUMMY_RESPONSE);

    // when
    client
        .newEvaluateConditionalCommand()
        .variables("{\"x\":100}")
        .tenantId("custom-tenant")
        .send()
        .join();

    // then
    final ConditionalEvaluationInstruction request =
        gatewayService.getLastRequest(ConditionalEvaluationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo("custom-tenant");
  }

  @Test
  public void shouldEvaluateConditionalWithEmptyResponse() {
    // given
    gatewayService.onEvaluateConditionalRequest(new EvaluateConditionalResult());

    // when
    final EvaluateConditionalResponse response =
        client.newEvaluateConditionalCommand().variables("{\"x\":100}").send().join();

    // then
    assertThat(response.getProcessInstances()).isEmpty();
  }

  public static class Variables {

    Variables() {}

    public String getFoo() {
      return "bar";
    }
  }
}
