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

import static io.camunda.client.api.search.response.UserTaskState.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.response.UserTaskState;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    client.newUserTaskSearchRequest().filter(f -> f.state(COMPLETED)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getState()).isEqualTo(UserTaskFilter.StateEnum.COMPLETED);
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
    assertThat(request.getFilter().getTenantId()).isEqualTo("tenant1");
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
    final Map<String, Object> map = new HashMap<>();
    map.put("test", "test");
    final UserTaskVariableFilterRequest userTaskVariableFilterRequest =
        new UserTaskVariableFilterRequest()
            .name("test")
            .value(new StringPropertyImpl().eq("test").build());
    final ArrayList<UserTaskVariableFilterRequest> listFilter = new ArrayList<>();

    listFilter.add(userTaskVariableFilterRequest);

    client.newUserTaskSearchRequest().filter(f -> f.processInstanceVariables(map)).send().join();

    // then
    final UserTaskSearchQuery request = gatewayService.getLastRequest(UserTaskSearchQuery.class);
    assertThat(request.getFilter().getProcessInstanceVariables()).isEqualTo(listFilter);
  }

  @Test
  void shouldSearchUserTaskByLocalVariable() {
    // when
    final UserTaskVariableFilterRequest userTaskVariableFilterRequest =
        new UserTaskVariableFilterRequest()
            .name("test")
            .value(new StringPropertyImpl().eq("test").build());
    final ArrayList<UserTaskVariableFilterRequest> listFilter = new ArrayList<>();

    listFilter.add(userTaskVariableFilterRequest);

    client.newUserTaskSearchRequest().filter(f -> f.localVariables(listFilter)).send().join();

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

  @Test
  public void shouldConvertUserTaskState() {

    for (final UserTaskState value : UserTaskState.values()) {
      final UserTaskFilter.StateEnum protocolValue = UserTaskState.toProtocolState(value);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(UserTaskFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final UserTaskResult.StateEnum protocolValue : UserTaskResult.StateEnum.values()) {
      final UserTaskState value = UserTaskState.fromProtocolState(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue == UserTaskResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }
}
