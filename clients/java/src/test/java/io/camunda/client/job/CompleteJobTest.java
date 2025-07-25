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
package io.camunda.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.protocol.rest.JobResult.TypeEnum;
import io.camunda.client.util.ClientTest;
import io.camunda.client.util.JsonUtil;
import io.camunda.client.util.StringUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResultCorrections;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StringList;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.mockito.Mockito;

public final class CompleteJobTest extends ClientTest {

  public static final String USER_TASK_DISCRIMINATOR = TypeEnum.USER_TASK.getValue();
  public static final String AD_HOC_SUB_PROCESS_DISCRIMINATOR =
      TypeEnum.AD_HOC_SUB_PROCESS.getValue();

  @Test
  public void shouldCompleteJobByKey() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getVariables()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJob() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getVariables()).isEmpty();
    // gRPC provides the result with default value, so checking for default property value here.
    assertThat(request.getResult().getDenied()).isFalse();
    assertThat(request.getResult().getDeniedReason()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newCompleteCommand(jobKey).variables(json).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), json);
  }

  @Test
  public void shouldCompleteWithJsonStreamVariables() {
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
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), json);
  }

  @Test
  public void shouldCompleteWithJsonMapVariables() {
    // given
    final long jobKey = 12;
    final Map<String, Object> map = Collections.singletonMap("key", "val");

    // when
    client.newCompleteCommand(jobKey).variables(map).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(map);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), expectedJson);
  }

  @Test
  public void shouldCompleteWithJsonPOJOVariables() {

    // given
    final long jobKey = 12;
    final POJO pojo = new POJO();
    pojo.setKey("val");

    // when
    client.newCompleteCommand(jobKey).variables(pojo).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(pojo);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), expectedJson);
  }

  @Test
  public void shouldCompleteWithSingleVariable() {

    // given
    final long jobKey = 12;
    final String key = "key";
    final String value = "value";

    // when
    client.newCompleteCommand(jobKey).variable(key, value).send().join();

    // then
    final String expectedVariable = JsonUtil.toJson(Collections.singletonMap(key, value));

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), expectedVariable);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newCompleteCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final CompleteJobCommandStep1 command = client.newCompleteCommand(12);

    // when
    final CompleteJobResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldCompleteJobWithEmptyResult() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(CompleteJobCommandJobResultStep::forUserTask)
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isFalse();
    assertThat(request.getResult().getDeniedReason()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultDeniedFalse() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).withResult(r -> r.forUserTask().deny(false)).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isFalse();
    assertThat(request.getResult().getDeniedReason()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultDeniedTrueAndDeniedReason() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r -> r.forUserTask().deny(true).deniedReason("Reason to deny lifecycle transition"))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isTrue();
    assertThat(request.getResult().getDeniedReason())
        .isEqualTo("Reason to deny lifecycle transition");

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultDeniedWithAndDeniedReason() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forUserTask().deny(true, "Reason to deny lifecycle transition"))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isTrue();
    assertThat(request.getResult().getDeniedReason())
        .isEqualTo("Reason to deny lifecycle transition");

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultObjectDeniedFalse() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).withResult(r -> r.forUserTask().deny(false)).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isFalse();
    assertThat(request.getResult().getDeniedReason()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultObjectDeniedTrue() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r -> r.forUserTask().deny(true).deniedReason("Reason to deny lifecycle transition"))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isTrue();
    assertThat(request.getResult().getDeniedReason())
        .isEqualTo("Reason to deny lifecycle transition");

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultDone() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forUserTask().deny(false))
        .variable("we_can", "still_set_vars")
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getResult().getDenied()).isFalse();
    assertThat(request.getResult().getDeniedReason()).isEmpty();
    assertThat(request.getVariables()).isEqualTo("{\"we_can\":\"still_set_vars\"}");

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultCorrectionSet() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forUserTask()
                    .correctAssignee("Test")
                    .correctDueDate("due date")
                    .correctFollowUpDate("follow up date")
                    .correctCandidateUsers(Arrays.asList("User A", "User B"))
                    .correctCandidateGroups(Arrays.asList("Group A", "Group B"))
                    .correctPriority(80))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(
                        JobResultCorrections.newBuilder()
                            .setAssignee("Test")
                            .setDueDate("due date")
                            .setFollowUpDate("follow up date")
                            .setCandidateUsers(
                                StringList.newBuilder()
                                    .addAllValues(Arrays.asList("User A", "User B"))
                                    .build())
                            .setCandidateGroups(
                                StringList.newBuilder()
                                    .addAllValues(Arrays.asList("Group A", "Group B"))
                                    .build())
                            .setPriority(80)
                            .build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultCorrectionPartiallySet() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forUserTask()
                    .correctAssignee("Test")
                    .correctDueDate("due date")
                    .correctFollowUpDate("")
                    .correctCandidateUsers(Arrays.asList("User A", "User B"))
                    .correctPriority(80))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(
                        JobResultCorrections.newBuilder()
                            .setAssignee("Test")
                            .setDueDate("due date")
                            .setFollowUpDate("")
                            .setCandidateUsers(
                                StringList.newBuilder()
                                    .addAllValues(Arrays.asList("User A", "User B"))
                                    .build())
                            .setPriority(80)
                            .build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultCorrectionUsingNullExplicitly() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forUserTask()
                    .correctAssignee(null)
                    .correctDueDate(null)
                    .correctFollowUpDate(null)
                    .correctCandidateGroups(null)
                    .correctCandidateUsers(null)
                    .correctPriority(null))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(
                        JobResultCorrections.newBuilder()
                            .clearAssignee()
                            .clearCandidateGroups()
                            .clearCandidateUsers()
                            .clearDueDate()
                            .clearFollowUpDate()
                            .clearPriority()
                            .build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultPartiallySet() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forUserTask()
                    .deny(false)
                    .correctAssignee("Test")
                    .correctDueDate(null)
                    .correctFollowUpDate("")
                    .correctCandidateUsers(Arrays.asList("User A", "User B"))
                    .correctPriority(80))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(
                        JobResultCorrections.newBuilder()
                            .setAssignee("Test")
                            .clearDueDate()
                            .setFollowUpDate("")
                            .setCandidateUsers(
                                StringList.newBuilder()
                                    .addAllValues(Arrays.asList("User A", "User B"))
                                    .build())
                            .clearCandidateGroups()
                            .setPriority(80)
                            .build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithResultCorrectionsObject() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forUserTask()
                    .deny(false)
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
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(
                        JobResultCorrections.newBuilder()
                            .setAssignee("Test")
                            .clearDueDate()
                            .setFollowUpDate("")
                            .setCandidateUsers(
                                StringList.newBuilder()
                                    .addAllValues(Arrays.asList("User A", "User B"))
                                    .build())
                            .clearCandidateGroups()
                            .setPriority(80)
                            .build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJobWithDefaultResultCorrection() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(CompleteJobCommandJobResultStep::forUserTask)
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(USER_TASK_DISCRIMINATOR)
                    .setDenied(false)
                    .setDeniedReason("")
                    .setCorrections(JobResultCorrections.newBuilder().build())
                    .build())
            .build();

    assertThat(request).isEqualTo(expectedRequest);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteAdHocSubProcessWithElementIdAndNoVariables() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    final String elementId = "elementId";

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forAdHocSubProcess().activateElement(elementId))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(AD_HOC_SUB_PROCESS_DISCRIMINATOR)
                    .addActivateElements(
                        GatewayOuterClass.JobResultActivateElement.newBuilder()
                            .setElementId(elementId)
                            .build())
                    .setIsCompletionConditionFulfilled(false)
                    .setIsCancelRemainingInstances(false))
            .build();

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  public void shouldCompleteAdHocSubProcessWithElementIdAndVariables() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    final String elementId = "elementId";
    final String variables = "{\"key\":\"value\",\"anotherKey\":\"anotherValue\"}";

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forAdHocSubProcess().activateElement(elementId).variables(variables))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(AD_HOC_SUB_PROCESS_DISCRIMINATOR)
                    .addActivateElements(
                        GatewayOuterClass.JobResultActivateElement.newBuilder()
                            .setElementId(elementId)
                            .setVariables(variables)
                            .build())
                    .setIsCompletionConditionFulfilled(false)
                    .setIsCancelRemainingInstances(false))
            .build();

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  public void shouldCompleteAdHocSubProcessWithMultipleElementIds() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    final String elementId1 = "elementId1";
    final String elementId2 = "elementId2";
    final String elementId3 = "elementId3";

    // when
    client
        .newCompleteCommand(job)
        .withResult(
            r ->
                r.forAdHocSubProcess()
                    .activateElement(elementId1)
                    .variable("key", "value")
                    .activateElement(elementId2)
                    .variable("anotherKey", "anotherValue")
                    .activateElement(elementId3))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(AD_HOC_SUB_PROCESS_DISCRIMINATOR)
                    .addActivateElements(
                        GatewayOuterClass.JobResultActivateElement.newBuilder()
                            .setElementId(elementId1)
                            .setVariables("{\"key\":\"value\"}")
                            .build())
                    .addActivateElements(
                        GatewayOuterClass.JobResultActivateElement.newBuilder()
                            .setElementId(elementId2)
                            .setVariables("{\"anotherKey\":\"anotherValue\"}")
                            .build())
                    .addActivateElements(
                        GatewayOuterClass.JobResultActivateElement.newBuilder()
                            .setElementId(elementId3)
                            .build())
                    .setIsCompletionConditionFulfilled(false)
                    .setIsCancelRemainingInstances(false))
            .build();

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  public void shouldCompleteAdHocSubProcessWithCompletionConditionFulfilled() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forAdHocSubProcess().completionConditionFulfilled(true))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(AD_HOC_SUB_PROCESS_DISCRIMINATOR)
                    .setIsCompletionConditionFulfilled(true)
                    .setIsCancelRemainingInstances(false))
            .build();

    assertThat(request).isEqualTo(expectedRequest);
  }

  @Test
  public void shouldCompleteAdHocSubProcessWithCancelRemainingInstances() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client
        .newCompleteCommand(job)
        .withResult(r -> r.forAdHocSubProcess().cancelRemainingInstances(true))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();

    final CompleteJobRequest expectedRequest =
        CompleteJobRequest.newBuilder()
            .setJobKey(job.getKey())
            .setResult(
                JobResult.newBuilder()
                    .setType(AD_HOC_SUB_PROCESS_DISCRIMINATOR)
                    .setIsCompletionConditionFulfilled(false)
                    .setIsCancelRemainingInstances(true))
            .build();

    assertThat(request).isEqualTo(expectedRequest);
  }

  public static class POJO {

    private String key;

    public String getKey() {
      return key;
    }

    public void setKey(final String key) {
      this.key = key;
    }
  }
}
