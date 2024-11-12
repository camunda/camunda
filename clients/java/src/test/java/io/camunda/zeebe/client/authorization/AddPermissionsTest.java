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
package io.camunda.zeebe.client.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.google.common.collect.Sets;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.zeebe.client.protocol.rest.PermissionDTO;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public final class AddPermissionsTest extends ClientRestTest {

  @Test
  void shouldSendCommand() {
    // when
    client
        .newAddPermissionsCommand(1L)
        .resourceType(ResourceTypeEnum.DEPLOYMENT)
        .permission(PermissionTypeEnum.CREATE)
        .resourceId("resourceId1")
        .permission(PermissionTypeEnum.UPDATE)
        .resourceIds(Arrays.asList("resourceId2", "resourceId3"))
        .permission(PermissionTypeEnum.DELETE)
        .resourceIds(Arrays.asList("resourceId4", "resourceId5"))
        .resourceId("resourceId6")
        .send()
        .join();

    // then
    final AuthorizationPatchRequest request =
        gatewayService.getLastRequest(AuthorizationPatchRequest.class);
    assertThat(request.getAction()).isEqualTo(ActionEnum.ADD);
    assertThat(request.getResourceType()).isEqualTo(ResourceTypeEnum.DEPLOYMENT);

    assertThat(request.getPermissions())
        .hasSize(3)
        .extracting(PermissionDTO::getPermissionType, PermissionDTO::getResourceIds)
        .containsExactly(
            tuple(
                PermissionTypeEnum.CREATE,
                Sets.newHashSet(Collections.singletonList("resourceId1"))),
            tuple(PermissionTypeEnum.UPDATE, Sets.newHashSet("resourceId2", "resourceId3")),
            tuple(
                PermissionTypeEnum.DELETE,
                Sets.newHashSet("resourceId4", "resourceId5", "resourceId6")));
  }

  @Test
  void shouldRaiseExceptionOnNullResourceType() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(null)
                    .permission(PermissionTypeEnum.CREATE)
                    .resourceId("resourceId")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resourceType must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullPermissionType() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(ResourceTypeEnum.DEPLOYMENT)
                    .permission(null)
                    .resourceId("resourceId")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("permissionType must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullResourceId() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(ResourceTypeEnum.DEPLOYMENT)
                    .permission(PermissionTypeEnum.CREATE)
                    .resourceId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resourceId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyResourceId() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(ResourceTypeEnum.DEPLOYMENT)
                    .permission(PermissionTypeEnum.CREATE)
                    .resourceId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resourceId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullResourceIds() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(ResourceTypeEnum.DEPLOYMENT)
                    .permission(PermissionTypeEnum.CREATE)
                    .resourceIds(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resourceIds must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyResourceIds() {
    // when then
    assertThatThrownBy(
            () ->
                client
                    .newAddPermissionsCommand(1L)
                    .resourceType(ResourceTypeEnum.DEPLOYMENT)
                    .permission(PermissionTypeEnum.CREATE)
                    .resourceIds(Collections.emptyList())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resourceIds must not be empty");
  }
}
