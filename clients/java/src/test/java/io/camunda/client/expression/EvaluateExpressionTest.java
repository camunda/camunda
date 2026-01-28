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
import io.camunda.client.protocol.rest.ExpressionEvaluationRequest;
import io.camunda.client.protocol.rest.ExpressionEvaluationResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class EvaluateExpressionTest extends ClientRestTest {

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
    final List<String> warnings = Arrays.asList("Warning 1", "Warning 2");
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
    assertThat(response.getWarnings()).containsExactly("Warning 1", "Warning 2");
  }
}
