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
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.protocol.rest.GroupChangeset;
import io.camunda.client.protocol.rest.GroupUpdateRequest;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class UpdateGroupTest extends ClientRestTest {

  private static final long GROUP_KEY = 123L;
  private static final String UPDATED_NAME = "Updated Group Name";

  @Test
  void shouldUpdateGroup() {
    // when
    client.newUpdateGroupCommand(GROUP_KEY).updateName(UPDATED_NAME).send().join();

    // then
    final GroupUpdateRequest request = gatewayService.getLastRequest(GroupUpdateRequest.class);
    assertThat(request.getChangeset().getName()).isEqualTo(UPDATED_NAME);
  }

  @Test
  void shouldUpdateGroupWithChangeset() {
    // when
    final GroupChangeset changeset = new GroupChangeset().name(UPDATED_NAME);
    client
        .newUpdateGroupCommand(GROUP_KEY)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final GroupUpdateRequest request = gatewayService.getLastRequest(GroupUpdateRequest.class);
    assertThat(request.getChangeset().getName()).isEqualTo(UPDATED_NAME);
  }

  @Test
  void shouldRaiseExceptionOnNotFoundGroup() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/groups/" + GROUP_KEY,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newUpdateGroupCommand(GROUP_KEY).updateName(UPDATED_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
