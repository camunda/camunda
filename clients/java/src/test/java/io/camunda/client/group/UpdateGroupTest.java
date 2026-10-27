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

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.GroupUpdateRequest;
import io.camunda.client.protocol.rest.GroupUpdateResult;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class UpdateGroupTest extends ClientRestTest {

  private static final String GROUP_ID = "groupId";
  private static final String UPDATED_NAME = "Updated Group Name";
  private static final String UPDATED_DESCRIPTION = "Updated Group Description";

  @Test
  void shouldUpdateGroup() {
    // given
    gatewayService.onUpdateGroupRequest(GROUP_ID, Instancio.create(GroupUpdateResult.class));

    // when
    client
        .newUpdateGroupCommand(GROUP_ID)
        .name(UPDATED_NAME)
        .description(UPDATED_DESCRIPTION)
        .send()
        .join();

    // then
    final GroupUpdateRequest request = gatewayService.getLastRequest(GroupUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
    assertThat(request.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
  }

  @Test
  void shouldUpdateGroupWithEmptyDescription() {
    // given
    gatewayService.onUpdateGroupRequest(GROUP_ID, Instancio.create(GroupUpdateResult.class));

    // when
    client.newUpdateGroupCommand(GROUP_ID).name(UPDATED_NAME).description("").send().join();

    // then
    final GroupUpdateRequest request = gatewayService.getLastRequest(GroupUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
    assertThat(request.getDescription()).isEqualTo("");
  }

  @Test
  void shouldRaiseExceptionOnNotFoundGroup() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/groups/" + GROUP_ID,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateGroupCommand(GROUP_ID)
                    .name(UPDATED_NAME)
                    .description(UPDATED_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
