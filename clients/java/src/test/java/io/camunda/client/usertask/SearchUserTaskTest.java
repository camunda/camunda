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
package io.camunda.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.builder.UserTaskStateProperty;
import io.camunda.client.protocol.rest.IntegerFilterProperty;
import io.camunda.client.protocol.rest.StringFilterProperty;
import io.camunda.client.protocol.rest.UserTaskFilter;
import io.camunda.client.protocol.rest.UserTaskSearchQuery;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import io.camunda.client.protocol.rest.UserTaskStateFilterProperty;
import io.camunda.client.protocol.rest.VariableValueFilterProperty;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class SearchUserTaskTest extends ClientRestTest {

  @Test
  void shouldSearchUserTask() {
    // when
    client.newUserTaskSearchRequest().send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchUserTaskByAssignee() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.assignee("demo")).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getAssignee().get$Eq()).isEqualTo("demo");
  }

  @Test
  void shouldSearchUserTaskByAssigneeStringFilter() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.assignee(b -> b.neq("that"))).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getAssignee().get$Neq()).isEqualTo("that");
  }

  @Test
  void shouldSearchUserTaskByState() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.state(UserTaskState.COMPLETED)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getState().get$Eq()).isEqualTo(UserTaskStateEnum.COMPLETED);
  }

  static Stream<Arguments> provideStateFilters() {
    return Stream.of(
        stateFilterCase(
            "eq:CANCELING",
            f -> f.eq(UserTaskState.CANCELING),
            f -> assertThat(f.get$Eq()).isEqualTo(UserTaskStateEnum.CANCELING)),
        stateFilterCase(
            "neq:COMPLETED",
            f -> f.neq(UserTaskState.COMPLETED),
            f -> assertThat(f.get$Neq()).isEqualTo(UserTaskStateEnum.COMPLETED)),
        stateFilterCase(
            "exists:true", f -> f.exists(true), f -> assertThat(f.get$Exists()).isTrue()),
        stateFilterCase(
            "exists:false", f -> f.exists(false), f -> assertThat(f.get$Exists()).isFalse()),
        stateFilterCase(
            "in:[ASSIGNING,UPDATING]",
            f -> f.in(UserTaskState.ASSIGNING, UserTaskState.UPDATING),
            f ->
                assertThat(f.get$In())
                    .containsExactly(UserTaskStateEnum.ASSIGNING, UserTaskStateEnum.UPDATING)),
        stateFilterCase(
            "like:CREAT*",
            f -> f.like("CREAT*"),
            f -> assertThat(f.get$Like()).isEqualTo("CREAT*")));
  }

  private static Arguments stateFilterCase(
      final String filterLabel,
      final Consumer<UserTaskStateProperty> filter,
      final Consumer<UserTaskStateFilterProperty> assertion) {
    return Arguments.of(
        Named.of("state(" + filterLabel + ")", filter),
        Named.of("assert " + filterLabel, assertion));
  }

  @ParameterizedTest(name = "[{index}] should apply {0}")
  @MethodSource("provideStateFilters")
  @DisplayName("Should search user tasks by state using advanced filters")
  void shouldSearchUserTasksByStateUsingAdvancedFilter(
      final Consumer<UserTaskStateProperty> filter,
      final Consumer<UserTaskStateFilterProperty> assertion) {

    // when
    client.newUserTaskSearchRequest().filter(f -> f.state(filter)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getState()).isNotNull().satisfies(assertion);
  }

  @Test
  void shouldSearchUserTaskByKey() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.userTaskKey(12345L)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getUserTaskKey()).isEqualTo("12345");
  }

  @Test
  void shouldSearchUserTaskByTaskDefinitionId() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.elementId("task-def-id")).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getElementId()).isEqualTo("task-def-id");
  }

  @Test
  void shouldSearchUserTaskByCandidateGroup() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.candidateGroup("group1")).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCandidateGroup().get$Eq()).isEqualTo("group1");
  }

  @Test
  void shouldSearchUserTaskByCandidateUser() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.candidateUser("user1")).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCandidateUser().get$Eq()).isEqualTo("user1");
  }

  @Test
  void shouldSearchUserTaskByProcessDefinitionKey() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.processDefinitionKey(123L)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionKey()).isEqualTo("123");
  }

  @Test
  void shouldSearchUserTaskByProcessInstanceKey() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.processInstanceKey(456L)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getProcessInstanceKey()).isEqualTo("456");
  }

  @Test
  void shouldSearchUserTaskByTenantId() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.tenantId("tenant1")).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getTenantId().get$Eq()).isEqualTo("tenant1");
  }

  @Test
  void shouldSearchUserTaskByPriority() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.priority(10)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getPriority().get$Eq()).isEqualTo(10);
  }

  @Test
  void shouldSearchUserTaskByPriorityLongFilter() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.priority(b -> b.gt(1).lt(10))).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    final UserTaskFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final IntegerFilterProperty priority = filter.getPriority();
    assertThat(priority).isNotNull();
    assertThat(priority.get$Gt()).isEqualTo(1);
    assertThat(priority.get$Lt()).isEqualTo(10);
  }

  @Test
  void shouldSearchUserTaskByProcessInstanceVariable() {
    // when
    final VariableValueFilterProperty userTaskVariableFilterProperty =
        new VariableValueFilterProperty()
            .name("test")
            .value(new StringFilterProperty().$eq("test"));
    final ArrayList<VariableValueFilterProperty> listFilter = new ArrayList<>();

    listFilter.add(userTaskVariableFilterProperty);

    client
        .newUserTaskSearchRequest()
        .filter(f -> f.processInstanceVariables(Collections.singletonMap("test", "test")))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getProcessInstanceVariables()).isEqualTo(listFilter);
  }

  @Test
  void shouldSearchUserTaskByLocalVariable() {
    // when
    final VariableValueFilterProperty userTaskVariableFilterProperty =
        new VariableValueFilterProperty()
            .name("test")
            .value(new StringFilterProperty().$eq("test"));
    final ArrayList<VariableValueFilterProperty> listFilter = new ArrayList<>();

    listFilter.add(userTaskVariableFilterProperty);

    client
        .newUserTaskSearchRequest()
        .filter(f -> f.localVariables(Collections.singletonMap("test", "test")))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getLocalVariables()).isEqualTo(listFilter);
  }

  @Test
  void shouldReturnFormByUserTaskKey() {
    // when
    final long userTaskKey = 1L;
    client.newUserTaskGetFormRequest(userTaskKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/user-tasks/" + userTaskKey + "/form");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  void shouldReturnUserTaskByCreationDateExists() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.exists(true)))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Exists()).isTrue();
  }

  @Test
  void shouldReturnUserTaskByCreationDateNotExists() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.exists(false)))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Exists()).isFalse();
  }

  @Test
  void shouldReturnUserTaskByCreationDateGt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.gt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Gt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCreationDateLt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.lt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Lt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCreationDateGte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.gte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Gte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCreationDateLte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.lte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Lte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCreationDateEq() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.creationDate(b -> b.eq(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCreationDate().get$Eq()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.completionDate(b -> b.gte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCompletionDate().get$Gte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCompletionDateLte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.completionDate(b -> b.lte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCompletionDate().get$Lte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGteLte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(
            f ->
                f.completionDate(
                    b -> b.gte(OffsetDateTime.now().minusDays(3)).lte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCompletionDate().get$Lte()).isNotNull();
    assertThat(request.getFilter().getCompletionDate().get$Gte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGtLt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(
            f ->
                f.completionDate(
                    b -> b.gt(OffsetDateTime.now().minusDays(3)).lt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getCompletionDate().get$Lt()).isNotNull();
    assertThat(request.getFilter().getCompletionDate().get$Gt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByDueDateExists() {
    // when
    client.newUserTaskSearchRequest().filter(f -> f.dueDate(b -> b.exists(true))).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getDueDate().get$Exists()).isTrue();
  }

  @Test
  void shouldReturnUserTaskByDueDateGt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.dueDate(b -> b.gt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getDueDate().get$Gt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByDueDateLt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.dueDate(b -> b.lt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getDueDate().get$Lt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByDueDateGteLte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(
            f -> f.dueDate(b -> b.gte(OffsetDateTime.now().minusDays(5)).lte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getDueDate().get$Gte()).isNotNull();
    assertThat(request.getFilter().getDueDate().get$Lte()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByFollowUpDateExists() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.followUpDate(b -> b.exists(true)))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getFollowUpDate().get$Exists()).isTrue();
  }

  @Test
  void shouldReturnUserTaskByFollowUpDateGt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.followUpDate(b -> b.gt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getFollowUpDate().get$Gt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByFollowUpDateLt() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(f -> f.followUpDate(b -> b.lt(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getFollowUpDate().get$Lt()).isNotNull();
  }

  @Test
  void shouldReturnUserTaskByFollowUpDateGteLte() {
    // when
    client
        .newUserTaskSearchRequest()
        .filter(
            f ->
                f.followUpDate(
                    b -> b.gte(OffsetDateTime.now().minusDays(10)).lte(OffsetDateTime.now())))
        .send()
        .join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getFollowUpDate().get$Gte()).isNotNull();
    assertThat(request.getFilter().getFollowUpDate().get$Lte()).isNotNull();
  }
}
