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
package io.camunda.client.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.GroupCreateRequest;
import io.camunda.client.protocol.rest.GroupCreateResult;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class CreateGroupTest extends ClientRestTest {

  public static final String GROUP_ID = "groupId";
  public static final String NAME = "groupName";
  public static final String DESCRIPTION = "groupDescription";

  @Test
  void shouldCreateGroup() {
    // given
    gatewayService.onCreateGroupRequest(Instancio.create(GroupCreateResult.class));

    // when
    client
        .newCreateGroupCommand()
        .groupId(GROUP_ID)
        .name(NAME)
        .description(DESCRIPTION)
        .send()
        .join();

    // then
    final GroupCreateRequest request = gatewayService.getLastRequest(GroupCreateRequest.class);
    assertThat(request.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(request.getName()).isEqualTo(NAME);
    assertThat(request.getDescription()).isEqualTo(DESCRIPTION);
  }

  @Test
  void shouldCreateGroupWithoutDescription() {
    // given
    gatewayService.onCreateGroupRequest(Instancio.create(GroupCreateResult.class));

    // when
    client.newCreateGroupCommand().groupId(GROUP_ID).name(NAME).send().join();

    // then
    final GroupCreateRequest request = gatewayService.getLastRequest(GroupCreateRequest.class);
    assertThat(request.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(request.getName()).isEqualTo(NAME);
    assertThat(request.getDescription()).isNull();
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getGroupsUrl(), () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newCreateGroupCommand().groupId(GROUP_ID).name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionIfGroupAlreadyExists() {
    // given
    gatewayService.onCreateGroupRequest(Instancio.create(GroupCreateResult.class));
    client.newCreateGroupCommand().groupId(GROUP_ID).name(NAME).send().join();

    gatewayService.errorOnRequest(
        RestGatewayPaths.getGroupsUrl(), () -> new ProblemDetail().title("Conflict").status(409));

    // when / then
    assertThatThrownBy(
            () -> client.newCreateGroupCommand().groupId(GROUP_ID).name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldHandleValidationErrorResponse() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getGroupsUrl(),
        () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () -> client.newCreateGroupCommand().groupId(GROUP_ID).name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
