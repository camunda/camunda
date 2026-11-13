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

import static io.camunda.client.util.assertions.LoggedRequestAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.LookupUserTaskResponse;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.UserTaskLookupRequest;
import io.camunda.client.protocol.rest.UserTaskLookupResult;
import io.camunda.client.protocol.rest.UserTaskLookupStateEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public final class LookupUserTaskTest extends ClientRestTest {

  private static final long USER_TASK_KEY = 2251799813685249L;

  @Test
  void shouldSendNoVariableNamesWhenFetchVariablesIsNotSet() {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult());

    // when
    client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getUserTaskLookupUrl(USER_TASK_KEY));
    assertThat(RestGatewayService.getLastRequest().getBodyAsString())
        .isEqualTo("{\"fetchVariables\":[]}");
  }

  @Test
  void shouldSendFetchVariablesAsList() {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult());

    // when
    client
        .newLookupUserTaskCommand(USER_TASK_KEY)
        .fetchVariables(Arrays.asList("recommendation", "applicant"))
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .extractingBody(UserTaskLookupRequest.class)
        .isEqualTo(
            new UserTaskLookupRequest()
                .fetchVariables(Arrays.asList("recommendation", "applicant")));
  }

  @Test
  void shouldSendFetchVariablesAsVarargs() {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult());

    // when
    client
        .newLookupUserTaskCommand(USER_TASK_KEY)
        .fetchVariables("recommendation", "applicant")
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .extractingBody(UserTaskLookupRequest.class)
        .isEqualTo(
            new UserTaskLookupRequest()
                .fetchVariables(Arrays.asList("recommendation", "applicant")));
  }

  @Test
  void shouldReplaceFetchVariablesWhenSetAgain() {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult());

    // when
    client
        .newLookupUserTaskCommand(USER_TASK_KEY)
        .fetchVariables("recommendation")
        .fetchVariables("applicant")
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .extractingBody(UserTaskLookupRequest.class)
        .isEqualTo(new UserTaskLookupRequest().fetchVariables(Arrays.asList("applicant")));
  }

  @Test
  void shouldReturnEveryField() {
    // given
    final OffsetDateTime creationDate = OffsetDateTime.parse("2026-11-04T10:15:30.123Z");
    final OffsetDateTime dueDate = OffsetDateTime.parse("2026-11-10T09:00:00Z");
    final OffsetDateTime followUpDate = OffsetDateTime.parse("2026-11-08T09:00:00+01:00");
    gatewayService.onLookupUserTaskRequest(
        USER_TASK_KEY,
        lookupResult()
            .assignee("demo")
            .candidateGroups(Arrays.asList("sales"))
            .candidateUsers(Arrays.asList("anna", "ben"))
            .dueDate(dueDate.toString())
            .followUpDate(followUpDate.toString())
            .creationDate(creationDate.toString())
            .formKey("7")
            .externalFormReference("https://forms.example/review")
            .customHeaders(Collections.singletonMap("region", "emea"))
            .priority(80)
            .tags(Collections.singleton("vip"))
            .businessId("order-42"));

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getUserTaskKey()).isEqualTo(USER_TASK_KEY);
    assertThat(response.getState()).isEqualTo(UserTaskState.CREATED);
    assertThat(response.getElementId()).isEqualTo("review");
    assertThat(response.getElementInstanceKey()).isEqualTo(1L);
    assertThat(response.getProcessInstanceKey()).isEqualTo(2L);
    assertThat(response.getRootProcessInstanceKey()).isEqualTo(3L);
    assertThat(response.getBpmnProcessId()).isEqualTo("order-process");
    assertThat(response.getProcessDefinitionKey()).isEqualTo(4L);
    assertThat(response.getProcessDefinitionVersion()).isEqualTo(5);
    assertThat(response.getTenantId()).isEqualTo("<default>");
    assertThat(response.getAssignee()).isEqualTo("demo");
    assertThat(response.getCandidateGroups()).containsExactly("sales");
    assertThat(response.getCandidateUsers()).containsExactly("anna", "ben");
    assertThat(response.getDueDate()).isEqualTo(dueDate);
    assertThat(response.getFollowUpDate()).isEqualTo(followUpDate);
    assertThat(response.getCreationDate()).isEqualTo(creationDate);
    assertThat(response.getFormKey()).isEqualTo(7L);
    assertThat(response.getExternalFormReference()).isEqualTo("https://forms.example/review");
    assertThat(response.getCustomHeaders()).containsExactly(entry("region", "emea"));
    assertThat(response.getPriority()).isEqualTo(80);
    assertThat(response.getTags()).containsExactly("vip");
    assertThat(response.getBusinessId()).isEqualTo("order-42");
  }

  @Test
  void shouldReturnNullForUnsetFields() {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult());

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getAssignee()).isNull();
    assertThat(response.getDueDate()).isNull();
    assertThat(response.getFollowUpDate()).isNull();
    assertThat(response.getFormKey()).isNull();
    assertThat(response.getExternalFormReference()).isNull();
    assertThat(response.getBusinessId()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserTaskLookupStateEnum.class,
      names = "UNKNOWN_DEFAULT_OPEN_API",
      mode = Mode.EXCLUDE)
  void shouldReturnState(final UserTaskLookupStateEnum state) {
    // given
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult().state(state));

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getState()).isEqualTo(UserTaskState.valueOf(state.name()));
  }

  @Test
  void shouldReturnUnknownEnumValueForStateAddedLater() {
    // given
    final String json =
        new CamundaObjectMapper()
            .toJson(lookupResult())
            .replace("\"state\":\"CREATED\"", "\"state\":\"NON_EXISTING_STATE\"");
    WireMock.stubFor(
        WireMock.post(RestGatewayPaths.getUserTaskLookupUrl(USER_TASK_KEY))
            .willReturn(WireMock.okJson(json)));

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getState()).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldReturnVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("applicant", "Jane");
    variables.put("amount", 1200);
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult().variables(variables));

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getVariablesAsMap())
        .containsOnly(entry("applicant", "Jane"), entry("amount", 1200));
    assertThat(response.getVariables()).contains("\"applicant\":\"Jane\"", "\"amount\":1200");
    assertThat(response.getVariablesAsType(LoanApplication.class))
        .isEqualTo(new LoanApplication("Jane", 1200));
    assertThat(response.getVariable("applicant")).isEqualTo("Jane");
  }

  @Test
  void shouldTellANullVariableFromAMissingOne() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("decision", null);
    gatewayService.onLookupUserTaskRequest(USER_TASK_KEY, lookupResult().variables(variables));

    // when
    final LookupUserTaskResponse response =
        client.newLookupUserTaskCommand(USER_TASK_KEY).send().join();

    // then
    assertThat(response.getVariablesAsMap()).containsEntry("decision", null);
    assertThat(response.getVariable("decision")).isNull();
    assertThat(response.getVariablesAsMap()).doesNotContainKey("missing");
    assertThatThrownBy(() -> response.getVariable("missing"))
        .isInstanceOf(ClientException.class)
        .hasMessage("The variable missing is not available");
  }

  @Test
  void shouldRaiseProblemExceptionWhenTaskIsNotFound() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getUserTaskLookupUrl(USER_TASK_KEY),
        () ->
            new ProblemDetail()
                .title("NOT_FOUND")
                .status(404)
                .detail("Expected to find an active user task with key, but none was found"));

    // when / then
    assertThatThrownBy(() -> client.newLookupUserTaskCommand(USER_TASK_KEY).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404")
        .satisfies(
            e -> {
              final io.camunda.client.api.ProblemDetail details = ((ProblemException) e).details();
              assertThat(details.getStatus()).isEqualTo(404);
              assertThat(details.getTitle()).isEqualTo("NOT_FOUND");
              assertThat(details.getDetail())
                  .isEqualTo("Expected to find an active user task with key, but none was found");
            });
  }

  private static UserTaskLookupResult lookupResult() {
    return new UserTaskLookupResult()
        .userTaskKey(String.valueOf(USER_TASK_KEY))
        .state(UserTaskLookupStateEnum.CREATED)
        .elementId("review")
        .elementInstanceKey("1")
        .processInstanceKey("2")
        .rootProcessInstanceKey("3")
        .processDefinitionId("order-process")
        .processDefinitionKey("4")
        .processDefinitionVersion(5)
        .tenantId("<default>")
        .creationDate(OffsetDateTime.parse("2026-11-04T10:15:30Z").toString());
  }

  public static final class LoanApplication {
    private String applicant;
    private int amount;

    public LoanApplication() {}

    LoanApplication(final String applicant, final int amount) {
      this.applicant = applicant;
      this.amount = amount;
    }

    public String getApplicant() {
      return applicant;
    }

    public void setApplicant(final String applicant) {
      this.applicant = applicant;
    }

    public int getAmount() {
      return amount;
    }

    public void setAmount(final int amount) {
      this.amount = amount;
    }

    @Override
    public boolean equals(final Object o) {
      if (!(o instanceof LoanApplication)) {
        return false;
      }
      final LoanApplication that = (LoanApplication) o;
      return amount == that.amount && Objects.equals(applicant, that.applicant);
    }

    @Override
    public int hashCode() {
      return Objects.hash(applicant, amount);
    }
  }
}
