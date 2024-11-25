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
package io.camunda.zeebe.client.tenant;

import static io.camunda.zeebe.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class UpdateTenantTest extends ClientRestTest {

  private static final long TENANT_KEY = 123L;
  private static final String UPDATED_NAME = "Updated Tenant Name";

  @Test
  void shouldUpdateTenant() {
    // when
    client.newUpdateTenantCommand(TENANT_KEY).name(UPDATED_NAME).send().join();

    // then
    final TenantUpdateRequest request = gatewayService.getLastRequest(TenantUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
  }

  @Test
  void shouldRaiseExceptionOnNullTenantKey() {
    // when / then
    assertThatThrownBy(() -> client.newUpdateTenantCommand(-1).name(UPDATED_NAME).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantKey must be greater than 0");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(() -> client.newUpdateTenantCommand(TENANT_KEY).name(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNotFoundTenant() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_KEY,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newUpdateTenantCommand(TENANT_KEY).name(UPDATED_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
