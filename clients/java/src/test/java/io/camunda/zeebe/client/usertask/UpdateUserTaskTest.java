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
package io.camunda.zeebe.client.usertask;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public final class UpdateUserTaskTest extends ClientRestTest {

  private static final String TEST_TIME =
      ZonedDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneId.of("UTC")).toString();

  @Test
  void shouldUpdateUserTask() {
    // when
    client.newUserTaskUpdateCommand(123L).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNull();
  }

  @Test
  void shouldUpdateUserTaskWithAction() {
    // when
    client.newUserTaskUpdateCommand(123L).action("foo").send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isEqualTo("foo");
    assertThat(request.getChangeset()).isNull();
  }

  @Test
  void shouldUpdateUserTaskWithDueDate() {
    // when
    client.newUserTaskUpdateCommand(123L).dueDate(TEST_TIME).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNotNull().containsOnly(entry("dueDate", TEST_TIME));
  }

  @Test
  void shouldUpdateUserTaskWithFollowUpDate() {
    // when
    client.newUserTaskUpdateCommand(123L).followUpDate(TEST_TIME).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNotNull().containsOnly(entry("followUpDate", TEST_TIME));
  }

  @Test
  void shouldUpdateUserTaskWithDueDateAndFollowUpDate() {
    // when
    client.newUserTaskUpdateCommand(123L).dueDate(TEST_TIME).followUpDate(TEST_TIME).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("followUpDate", TEST_TIME), entry("dueDate", TEST_TIME));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroup() {
    // when
    client.newUserTaskUpdateCommand(123L).candidateGroups("foo").send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateGroups", singletonList("foo")));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroups() {
    // when
    client.newUserTaskUpdateCommand(123L).candidateGroups("foo", "bar").send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateGroups", Arrays.asList("foo", "bar")));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroupsList() {
    // when
    client
        .newUserTaskUpdateCommand(123L)
        .candidateGroups(Arrays.asList("foo", "bar"))
        .send()
        .join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateGroups", Arrays.asList("foo", "bar")));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUser() {
    // when
    client.newUserTaskUpdateCommand(123L).candidateUsers("foo").send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateUsers", singletonList("foo")));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsers() {
    // when
    client.newUserTaskUpdateCommand(123L).candidateUsers("foo", "bar").send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateUsers", Arrays.asList("foo", "bar")));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsersList() {
    // when
    client.newUserTaskUpdateCommand(123L).candidateUsers(Arrays.asList("foo", "bar")).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateUsers", Arrays.asList("foo", "bar")));
  }

  @Test
  void shouldClearUserTaskDueDate() {
    // given
    final UpdateUserTaskCommandStep1 updateUserTaskCommandStep1 =
        client.newUserTaskUpdateCommand(123L).dueDate(TEST_TIME);

    // when
    updateUserTaskCommandStep1.clearDueDate().send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNotNull().containsOnly(entry("dueDate", ""));
  }

  @Test
  void shouldClearUserTaskFollowUpDate() {
    // given
    final UpdateUserTaskCommandStep1 updateUserTaskCommandStep1 =
        client.newUserTaskUpdateCommand(123L).followUpDate(TEST_TIME);

    // when
    updateUserTaskCommandStep1.clearFollowUpDate().send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNotNull().containsOnly(entry("followUpDate", ""));
  }

  @Test
  void shouldClearUserTaskCandidateGroups() {
    // given
    final UpdateUserTaskCommandStep1 updateUserTaskCommandStep1 =
        client.newUserTaskUpdateCommand(123L).candidateGroups("foo");

    // when
    updateUserTaskCommandStep1.clearCandidateGroups().send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateGroups", emptyList()));
  }

  @Test
  void shouldClearUserTaskCandidateUsers() {
    // given
    final UpdateUserTaskCommandStep1 updateUserTaskCommandStep1 =
        client.newUserTaskUpdateCommand(123L).candidateUsers("foo");

    // when
    updateUserTaskCommandStep1.clearCandidateUsers().send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset())
        .isNotNull()
        .containsOnly(entry("candidateUsers", emptyList()));
  }

  @Test
  void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getUserTaskUpdateUrl(123L),
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newUserTaskUpdateCommand(123L).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldUpdateTaskPriority() {
    // when
    client.newUserTaskUpdateCommand(123L).priority(95).send().join();

    // then
    final UserTaskUpdateRequest request =
        gatewayService.getLastRequest(UserTaskUpdateRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getChangeset()).isNotNull().containsOnly(entry("priority", 95));
  }

  @Test
  void shouldReturnErrorOnUpdateTaskPriority() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getUserTaskUpdateUrl(123L),
        () ->
            new ProblemDetail()
                .title("INVALID_ARGUMENT")
                .status(400)
                .detail("Priority field must be an integer between 0 and 100. Provided: 120"));

    // when / then
    assertThatThrownBy(() -> client.newUserTaskUpdateCommand(123L).priority(120).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Priority field must be an integer between 0 and 100. Provided: 120");
  }
}
