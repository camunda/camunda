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
package io.camunda.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.job.CompleteJobTest;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.JsonUtil;
import io.camunda.client.util.StringUtil;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class CompleteJobRestTest extends ClientRestTest {

  @Test
  void shouldCompleteJobByKey() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getVariables()).isNull();
    assertThat(request.getResult()).isNull();
  }

  @Test
  void shouldCompleteJob() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getVariables()).isNull();
  }

  @Test
  void shouldCompleteWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newCompleteCommand(jobKey).variables(json).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
  }

  @Test
  void shouldCompleteWithJsonStreamVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client
        .newCompleteCommand(jobKey)
        .variables(new ByteArrayInputStream(StringUtil.getBytes(json)))
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
  }

  @Test
  void shouldCompleteWithJsonMapVariables() {
    // given
    final long jobKey = 12;
    final Map<String, Object> map = Collections.singletonMap("key", "val");

    // when
    client.newCompleteCommand(jobKey).variables(map).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(map);

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedJson);
  }

  @Test
  void shouldCompleteWithJsonPOJOVariables() {

    // given
    final long jobKey = 12;
    final CompleteJobTest.POJO pojo = new CompleteJobTest.POJO();
    pojo.setKey("val");

    // when
    client.newCompleteCommand(jobKey).variables(pojo).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(pojo);

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedJson);
  }

  @Test
  void shouldCompleteWithSingleVariable() {

    // given
    final long jobKey = 12;
    final String key = "key";
    final String value = "value";

    // when
    client.newCompleteCommand(jobKey).variable(key, value).send().join();

    // then
    final String expectedVariable = JsonUtil.toJson(Collections.singletonMap(key, value));

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedVariable);
  }

  private static Stream<Arguments> denialDetails() {
    return Stream.of(
        Arguments.of(true, "Reason to deny lifecycle transition"), Arguments.of(false, null));
  }

  @ParameterizedTest
  @MethodSource("denialDetails")
  void shouldCompleteWithResult(final boolean denied, final String deniedReason) {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult()
        .deny(denied)
        .deniedReason(deniedReason)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult()).isNotNull();
    assertThat(request.getResult().getDenied()).isEqualTo(denied);
    assertThat(request.getResult().getDeniedReason()).isEqualTo(deniedReason);
  }

  @ParameterizedTest
  @MethodSource("denialDetails")
  void shouldCompleteWithResultDeniedWithReason(final boolean denied, final String deniedReason) {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).withResult().deny(denied, deniedReason).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult()).isNotNull();
    assertThat(request.getResult().getDenied()).isEqualTo(denied);
    assertThat(request.getResult().getDeniedReason()).isEqualTo(deniedReason);
  }

  @ParameterizedTest
  @MethodSource("denialDetails")
  void shouldCompleteWithResultObject(final boolean denied, final String deniedReason) {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult(r -> r.deny(denied).deniedReason(deniedReason))
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult()).isNotNull();
    assertThat(request.getResult().getDenied()).isEqualTo(denied);
    assertThat(request.getResult().getDeniedReason()).isEqualTo(deniedReason);
  }

  @Test
  void shouldCompleteJobWithResultDone() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult()
        .deny(false)
        .resultDone()
        .variable("we_can", "still_set_vars")
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult()).isNotNull();
    assertThat(request.getResult().getDenied()).isEqualTo(false);

    final Map<String, String> expectedVariables = new HashMap<>();
    expectedVariables.put("we_can", "still_set_vars");
    assertThat(request.getVariables()).isEqualTo(expectedVariables);
  }

  @Test
  void shouldCompleteWithResultDeniedNotSet() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).withResult().send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult()).isNotNull();
    assertThat(request.getResult().getDenied()).isNull();
    assertThat(request.getResult().getDeniedReason()).isNull();
  }

  @Test
  void shouldCompleteWithResultCorrectionsSet() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult()
        .correctAssignee("Test")
        .correctDueDate("due date")
        .correctFollowUpDate("follow up date")
        .correctCandidateUsers(Arrays.asList("User A", "User B"))
        .correctCandidateGroups(Arrays.asList("Group A", "Group B"))
        .correctPriority(80)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee("Test")
                            .dueDate("due date")
                            .followUpDate("follow up date")
                            .candidateUsers(Arrays.asList("User A", "User B"))
                            .candidateGroups(Arrays.asList("Group A", "Group B"))
                            .priority(80)));

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  void shouldCompleteWithResultCorrectionsPartiallySet() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult()
        .correctAssignee("Test")
        .correctDueDate("due date")
        .correctFollowUpDate("")
        .correctCandidateUsers(Arrays.asList("User A", "User B"))
        .correctPriority(80)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee("Test")
                            .dueDate("due date")
                            .followUpDate("")
                            .candidateUsers(Arrays.asList("User A", "User B"))
                            .candidateGroups(null)
                            .priority(80)));

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  void shouldCompleteWithResultCorrectionsUsingNullExplicitly() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult()
        .correctAssignee(null)
        .correctDueDate(null)
        .correctFollowUpDate(null)
        .correctCandidateGroups(null)
        .correctCandidateUsers(null)
        .correctPriority(null)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee(null)
                            .dueDate(null)
                            .followUpDate(null)
                            .candidateUsers(null)
                            .candidateGroups(null)
                            .priority(null)));

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  void shouldCompleteWithResultPartiallySet() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult(
            new CompleteJobResult()
                .deny(false)
                .correctAssignee("Test")
                .correctDueDate(null)
                .correctFollowUpDate("")
                .correctCandidateUsers(Arrays.asList("User A", "User B"))
                .correctPriority(80))
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .denied(false)
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee("Test")
                            .dueDate(null)
                            .followUpDate("")
                            .candidateUsers(Arrays.asList("User A", "User B"))
                            .candidateGroups(null)
                            .priority(80)));

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  void shouldCompleteWithResultCorrectionsObject() {
    // given
    final long jobKey = 12;

    // when
    client
        .newCompleteCommand(jobKey)
        .withResult(
            new CompleteJobResult()
                .correct(
                    c ->
                        c.assignee("Test")
                            .dueDate(null)
                            .followUpDate("")
                            .candidateUsers(Arrays.asList("User A", "User B"))
                            .priority(80)))
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .denied(false)
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee("Test")
                            .dueDate(null)
                            .followUpDate("")
                            .candidateUsers(Arrays.asList("User A", "User B"))
                            .candidateGroups(null)
                            .priority(80)));

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  void shouldCompleteWithDefaultResultCorrections() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).withResult().send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);

    final JobCompletionRequest expectedRequest =
        new JobCompletionRequest()
            .result(
                new JobResult()
                    .corrections(
                        new io.camunda.client.protocol.rest.JobResultCorrections()
                            .assignee(null)
                            .dueDate(null)
                            .followUpDate(null)
                            .candidateUsers(null)
                            .candidateGroups(null)
                            .priority(null)));

    assertThat(request).isEqualTo(expectedRequest);
  }
}
