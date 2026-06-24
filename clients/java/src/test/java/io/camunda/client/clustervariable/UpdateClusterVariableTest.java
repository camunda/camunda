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

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.ClusterVariableScopeEnum;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class UpdateClusterVariableTest extends ClientRestTest {

  private static final String VARIABLE_NAME = "myVariable";
  private static final String VARIABLE_VALUE = "myValue";
  private static final String TENANT_ID = "tenant_1";

  @Test
  void shouldUpdateGlobalScopedClusterVariable() {
    // given
    gatewayService.onUpdateGlobalClusterVariableRequest(
        VARIABLE_NAME,
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.GLOBAL));

    // when
    client
        .newGloballyScopedClusterVariableUpdateRequest()
        .update(VARIABLE_NAME, VARIABLE_VALUE)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(RestGatewayPaths.getClusterVariablesUpdateGlobalUrl(VARIABLE_NAME));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.PUT);
  }

  @Test
  void shouldUpdateTenantScopedClusterVariable() {
    // given
    gatewayService.onUpdateTenantClusterVariableRequest(
        TENANT_ID,
        VARIABLE_NAME,
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.TENANT));

    // when
    client
        .newTenantScopedClusterVariableUpdateRequest(TENANT_ID)
        .update(VARIABLE_NAME, VARIABLE_VALUE)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(RestGatewayPaths.getClusterVariablesUpdateTenantUrl(TENANT_ID, VARIABLE_NAME));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.PUT);
  }

  @Test
  void shouldRaiseExceptionOnNotFound() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesUpdateGlobalUrl(VARIABLE_NAME),
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(VARIABLE_NAME, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnServerError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesUpdateGlobalUrl(VARIABLE_NAME),
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(VARIABLE_NAME, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldRaiseExceptionOnNullVariableName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(null, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyVariableName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update("", VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullVariableValue() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(VARIABLE_NAME, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullTenantId() {
    // when / then
    assertThatThrownBy(() -> client.newTenantScopedClusterVariableUpdateRequest(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> client.newTenantScopedClusterVariableUpdateRequest(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }
}
