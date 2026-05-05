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
package io.camunda.client.expression;

import static io.camunda.client.util.assertions.LoggedRequestAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.api.response.EvaluationWarning;
import io.camunda.client.protocol.rest.ExpressionEvaluationRequest;
import io.camunda.client.protocol.rest.ExpressionEvaluationResult;
import io.camunda.client.protocol.rest.ExpressionEvaluationWarningItem;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class EvaluateExpressionTest extends ClientRestTest {

  private static Map<String, Object> variablesMap(
      final String k1, final Object v1, final String k2, final Object v2) {
    final Map<String, Object> map = new HashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  @Test
  void shouldEvaluateExpression() {
    // given
    final String expression = "=x + y";
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client.newEvaluateExpressionCommand().expression(expression).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(new ExpressionEvaluationRequest().expression(expression).tenantId("<default>"));
  }

  @Test
  void shouldEvaluateExpressionWithTenantId() {
    // given
    final String expression = "=x + y";
    final String tenantId = "tenant_123";
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client.newEvaluateExpressionCommand().expression(expression).tenantId(tenantId).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(new ExpressionEvaluationRequest().expression(expression).tenantId(tenantId));
  }

  @Test
  void shouldEvaluateExpressionWithVariablesAsMap() {
    // given
    final String expression = "=x + y";
    final Map<String, Object> variables = variablesMap("x", 10, "y", 20);
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client.newEvaluateExpressionCommand().expression(expression).variables(variables).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .variables(variables));
  }

  @Test
  void shouldEvaluateExpressionWithVariablesAsJsonString() {
    // given
    final String expression = "=x + y";
    final String variablesJson = "{\"x\": 10, \"y\": 20}";
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client
        .newEvaluateExpressionCommand()
        .expression(expression)
        .variables(variablesJson)
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .variables(variablesMap("x", 10, "y", 20)));
  }

  @Test
  void shouldEvaluateExpressionWithVariablesAsInputStream() {
    // given
    final String expression = "=x + y";
    final String variablesJson = "{\"x\": 10, \"y\": 20}";
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client
        .newEvaluateExpressionCommand()
        .expression(expression)
        .variables(new ByteArrayInputStream(variablesJson.getBytes(StandardCharsets.UTF_8)))
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .variables(variablesMap("x", 10, "y", 20)));
  }

  @Test
  void shouldEvaluateExpressionWithSingleVariable() {
    // given
    final String expression = "=x * 2";
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(20));

    // when
    client.newEvaluateExpressionCommand().expression(expression).variable("x", 10).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .variables(Collections.singletonMap("x", 10)));
  }

  @Test
  void shouldRaiseIllegalArgumentExceptionWhenExpressionIsNull() {
    // when / then
    assertThatThrownBy(() -> client.newEvaluateExpressionCommand().expression(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("expression must not be null");
  }

  @Test
  void shouldReceiveExpressionEvaluationResult() {
    // given
    final String expression = "=x + y";
    final Object resultValue = 30;
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult()
            .expression(expression)
            .result(resultValue)
            .warnings(Collections.emptyList()));

    // when
    final EvaluateExpressionResponse response =
        client.newEvaluateExpressionCommand().expression(expression).send().join();

    // then
    assertThat(response.getExpression()).isEqualTo(expression);
    assertThat(response.getResult()).isEqualTo(resultValue);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldReceiveExpressionEvaluationResultWithWarnings() {
    // given
    final String expression = "=x + y";
    final Object resultValue = 30;
    final List<ExpressionEvaluationWarningItem> warnings =
        Arrays.asList(
            new ExpressionEvaluationWarningItem().message("Warning 1"),
            new ExpressionEvaluationWarningItem().message("Warning 2"));
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult()
            .expression(expression)
            .result(resultValue)
            .warnings(warnings));

    // when
    final EvaluateExpressionResponse response =
        client.newEvaluateExpressionCommand().expression(expression).send().join();

    // then
    assertThat(response.getExpression()).isEqualTo(expression);
    assertThat(response.getResult()).isEqualTo(resultValue);
    assertThat(response.getWarnings())
        .extracting(EvaluationWarning::getMessage)
        .containsExactly("Warning 1", "Warning 2");
  }

  @Test
  void shouldEvaluateExpressionWithProcessInstanceKey() {
    // given
    final String expression = "=x + y";
    final long processInstanceKey = 1234567890L;
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client
        .newEvaluateExpressionCommand()
        .expression(expression)
        .processInstanceKey(processInstanceKey)
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .processInstanceKey(String.valueOf(processInstanceKey)));
  }

  @Test
  void shouldEvaluateExpressionWithElementInstanceKey() {
    // given
    final String expression = "=x + y";
    final long elementInstanceKey = 9876543210L;
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client
        .newEvaluateExpressionCommand()
        .expression(expression)
        .elementInstanceKey(elementInstanceKey)
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .elementInstanceKey(String.valueOf(elementInstanceKey)));
  }

  @Test
  void shouldEvaluateExpressionWithProcessInstanceKeyAndVariables() {
    // given
    final String expression = "=x + y";
    final long processInstanceKey = 1234567890L;
    final Map<String, Object> variables = variablesMap("x", 10, "y", 20);
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult().expression(expression).result(30));

    // when
    client
        .newEvaluateExpressionCommand()
        .expression(expression)
        .processInstanceKey(processInstanceKey)
        .variables(variables)
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getExpressionEvaluationUrl())
        .extractingBody(ExpressionEvaluationRequest.class)
        .isEqualTo(
            new ExpressionEvaluationRequest()
                .expression(expression)
                .tenantId("<default>")
                .processInstanceKey(String.valueOf(processInstanceKey))
                .variables(variables));
  }

  @Test
  void shouldRejectBothProcessInstanceKeyAndElementInstanceKey() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newEvaluateExpressionCommand()
                    .expression("=x + y")
                    .processInstanceKey(1L)
                    .elementInstanceKey(2L)
                    .send()
                    .join())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("processInstanceKey")
        .hasMessageContaining("elementInstanceKey")
        .hasMessageContaining("mutually exclusive");
  }

  @Test
  void shouldReceiveExpressionEvaluationResultWithProcessInstanceKey() {
    // given
    final String expression = "=x + y";
    final Object resultValue = 30;
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult()
            .expression(expression)
            .result(resultValue)
            .warnings(Collections.emptyList()));

    // when
    final EvaluateExpressionResponse response =
        client
            .newEvaluateExpressionCommand()
            .expression(expression)
            .processInstanceKey(1234567890L)
            .send()
            .join();

    // then
    assertThat(response.getExpression()).isEqualTo(expression);
    assertThat(response.getResult()).isEqualTo(resultValue);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldReceiveExpressionEvaluationResultWithElementInstanceKey() {
    // given
    final String expression = "=x + y";
    final Object resultValue = 30;
    gatewayService.onExpressionEvaluationRequest(
        new ExpressionEvaluationResult()
            .expression(expression)
            .result(resultValue)
            .warnings(Collections.emptyList()));

    // when
    final EvaluateExpressionResponse response =
        client
            .newEvaluateExpressionCommand()
            .expression(expression)
            .elementInstanceKey(9876543210L)
            .send()
            .join();

    // then
    assertThat(response.getExpression()).isEqualTo(expression);
    assertThat(response.getResult()).isEqualTo(resultValue);
    assertThat(response.getWarnings()).isEmpty();
  }
}
