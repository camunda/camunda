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
package io.camunda.client.tenant;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class AssignUserToTenantTest extends ClientRestTest {

  private static final String TENANT_ID = UUID.randomUUID().toString();
  private static final String USER_NAME = UUID.randomUUID().toString();

  @Test
  void shouldAssignUserToTenant() {
    // when
    client.newAssignUserToTenantCommand(TENANT_ID).username(USER_NAME).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    final RequestMethod method = RestGatewayService.getLastRequest().getMethod();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/users/" + USER_NAME);
    assertThat(method).isEqualTo(RequestMethod.PUT);
  }

  @Test
  void shouldRaiseExceptionOnNotFoundTenant() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/users/" + USER_NAME,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToTenantCommand(TENANT_ID).username(USER_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnNotFoundUser() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/users/" + USER_NAME,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToTenantCommand(TENANT_ID).username(USER_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldHandleServerError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/users/" + USER_NAME,
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToTenantCommand(TENANT_ID).username(USER_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequest() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/users/" + USER_NAME,
        () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToTenantCommand(TENANT_ID).username(USER_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }
}
