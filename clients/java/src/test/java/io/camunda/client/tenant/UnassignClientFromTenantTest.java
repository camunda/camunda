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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class UnassignClientFromTenantTest extends ClientRestTest {

  public static final String CLIENT_ID = "clientId";
  public static final String TENANT_ID = "tenantId";

  @Test
  void shouldUnassignClientFromTenant() {
    // when
    client
        .newUnassignClientFromTenantCommand()
        .clientId(CLIENT_ID)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(TENANT_ID + "/clients/" + CLIENT_ID)).isTrue();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.DELETE);
  }

  @Test
  void shouldRaiseExceptionOnNullClientIdWhenUnassigningClientFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignClientFromTenantCommand()
                    .clientId(null)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyClientIdWhenUnassigningClientFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignClientFromTenantCommand()
                    .clientId("")
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullTenantIdWhenUnassigningClientFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignClientFromTenantCommand()
                    .clientId(CLIENT_ID)
                    .tenantId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantIdWhenUnassigningClientFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignClientFromTenantCommand()
                    .clientId(CLIENT_ID)
                    .tenantId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }
}
