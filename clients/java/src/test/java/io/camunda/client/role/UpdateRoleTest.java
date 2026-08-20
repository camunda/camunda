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
package io.camunda.client.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.RoleUpdateRequest;
import io.camunda.client.protocol.rest.RoleUpdateResult;
import io.camunda.client.util.ClientRestTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class UpdateRoleTest extends ClientRestTest {
  private static final String ROLE_ID = "roleId";
  private static final String UPDATED_NAME = "Updated Role Name";
  private static final String UPDATED_DESCRIPTION = "Updated Role Description";

  @Test
  void shouldUpdateRole() {
    // given
    gatewayService.onUpdateRoleRequest(ROLE_ID, Instancio.create(RoleUpdateResult.class));

    // when
    client
        .newUpdateRoleCommand(ROLE_ID)
        .name(UPDATED_NAME)
        .description(UPDATED_DESCRIPTION)
        .send()
        .join();

    // then
    final RoleUpdateRequest request = gatewayService.getLastRequest(RoleUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
    assertThat(request.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
  }

  @Test
  void shouldRaiseExceptionOnEmptyName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name("")
                    .description(UPDATED_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name(null)
                    .description(UPDATED_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullDescription() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateRoleCommand(ROLE_ID)
                    .name(UPDATED_NAME)
                    .description(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("description must not be null");
  }
}
