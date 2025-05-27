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

import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

/** Unit tests for validation logic in AssignRoleToTenantCommand. */
public class AssignRoleToTenantTest extends ClientRestTest {

  private static final String TENANT_ID = "test-tenant";
  private static final String ROLE_ID = "test-role";

  @Test
  void shouldAssignRoleToTenant() {
    // when
    client.newAssignRoleToTenantCommand().roleId(ROLE_ID).tenantId(TENANT_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/roles/" + ROLE_ID);
  }

  @Test
  void shouldUnassignRoleFromTenant() {
    // when
    client.newUnassignRoleFromTenantCommand(TENANT_ID).roleId(ROLE_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/roles/" + ROLE_ID);
  }

  @Test
  void shouldRaiseExceptionOnNullTenantId() {
    assertThatThrownBy(
            () ->
                client.newAssignRoleToTenantCommand().roleId(ROLE_ID).tenantId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantId() {
    assertThatThrownBy(
            () -> client.newAssignRoleToTenantCommand().roleId(ROLE_ID).tenantId("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullRoleId() {
    assertThatThrownBy(
            () ->
                client
                    .newAssignRoleToTenantCommand()
                    .roleId(null)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyRoleId() {
    assertThatThrownBy(
            () ->
                client.newAssignRoleToTenantCommand().roleId("").tenantId(TENANT_ID).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }
}
