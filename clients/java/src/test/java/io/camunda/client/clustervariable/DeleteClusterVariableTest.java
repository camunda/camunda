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
package io.camunda.client.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class DeleteClusterVariableTest extends ClientRestTest {

  private static final String VARIABLE_NAME = "myVariable";
  private static final String TENANT_ID = "tenant_1";

  @Test
  void shouldDeleteGlobalScopedClusterVariable() {
    // when
    client.newClusterVariableDeleteRequest().globalScoped().name(VARIABLE_NAME).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/" + VARIABLE_NAME + "/GLOBAL");
  }

  @Test
  void shouldDeleteTenantScopedClusterVariable() {
    // when
    client
        .newClusterVariableDeleteRequest()
        .tenantScoped(TENANT_ID)
        .name(VARIABLE_NAME)
        .send()
        .join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(
            RestGatewayPaths.getClusterVariablesUrl()
                + "/"
                + VARIABLE_NAME
                + "/TENANT/"
                + TENANT_ID);
  }

  @Test
  void shouldRaiseExceptionOnNotFound() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesUrl() + "/" + VARIABLE_NAME + "/GLOBAL",
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newClusterVariableDeleteRequest()
                    .globalScoped()
                    .name(VARIABLE_NAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnServerError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesUrl() + "/" + VARIABLE_NAME + "/GLOBAL",
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newClusterVariableDeleteRequest()
                    .globalScoped()
                    .name(VARIABLE_NAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequest() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesUrl() + "/" + VARIABLE_NAME + "/GLOBAL",
        () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newClusterVariableDeleteRequest()
                    .globalScoped()
                    .name(VARIABLE_NAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }

  @Test
  void shouldRaiseExceptionOnNullVariableName() {
    // when / then
    assertThatThrownBy(
            () -> client.newClusterVariableDeleteRequest().globalScoped().name(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyVariableName() {
    // when / then
    assertThatThrownBy(
            () -> client.newClusterVariableDeleteRequest().globalScoped().name("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }
}
