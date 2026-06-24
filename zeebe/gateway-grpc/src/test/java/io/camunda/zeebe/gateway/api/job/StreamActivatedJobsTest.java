/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.Test;

public class StreamActivatedJobsTest extends GatewayTest {
  public static final long DEADLINE = 123123123L;
  private static final String WORKER = "testWorker";

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final String jobType = "testJob";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = Arrays.asList("foo", "bar");

    final List<ActivatedJob> streamedJobs =
        getStreamActivatedJobsRequest(jobType, WORKER, timeout, fetchVariables).getStreamedJobs();

    assertThat(streamedJobs).isEmpty();

    // when
    final ActivatedJobImpl activatedJob = new ActivatedJobImpl();
    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType(jobType);
    jobRecord.setWorker(WORKER);
    jobRecord.setDeadline(DEADLINE);
    final Map<String, Object> fetchedVariables = Map.of("foo", 1, "bar", 2);
    jobRecord.setVariables(MsgPackUtil.asMsgPack(fetchedVariables));
    jobRecord.setTags(Set.of("tag1", "tag2"));
    activatedJob.setRecord(jobRecord);

    jobStreamer.push(activatedJob).join();

    // then
    final ActivatedJob streamedActivatedJob = streamedJobs.get(0);
    assertThat(streamedActivatedJob.getType()).isEqualTo(jobType);
    assertThat(streamedActivatedJob.getWorker()).isEqualTo(WORKER);
    assertThat(streamedActivatedJob.getDeadline()).isEqualTo(DEADLINE);
    assertThat(activatedJob.jobRecord().getVariables()).isEqualTo(fetchedVariables);
    assertThat(activatedJob.jobRecord().getTenantId())
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(activatedJob.jobRecord().getJobKind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(activatedJob.jobRecord().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.UNSPECIFIED);
    assertThat(activatedJob.jobRecord().getTags()).containsExactlyInAnyOrder("tag1", "tag2");
  }

  @Test
  public void shouldNotPushJobsWhenNoneAvailableForJobType() {
    // given
    final String jobType1 = "testJob1";
    final String jobType2 = "testJob2";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    final List<ActivatedJob> streamedJobs1 =
        getStreamActivatedJobsRequest(jobType1, WORKER, timeout, fetchVariables).getStreamedJobs();
    getStreamActivatedJobsRequest(jobType2, WORKER, timeout, fetchVariables);

    // when
    final ActivatedJobImpl activatedJob = new ActivatedJobImpl();
    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType(jobType2); // only push job for job type 2
    activatedJob.setRecord(jobRecord);

    jobStreamer.push(activatedJob).join();

    // then
    assertThat(streamedJobs1).isEmpty();
  }

  @Test
  public void shouldRemoveStreamOnError() {
    // given
    final String jobType = "testJobOnError";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequest(jobType, WORKER, timeout, fetchVariables);

    assertThat(jobStreamer.containsStreamFor(jobType)).isTrue();

    // when
    streamObserver.onError(new RuntimeException("test on error"));

    // then
    Awaitility.await("until the stream is removed")
        .until(() -> !jobStreamer.containsStreamFor(jobType));
  }

  @Test
  public void shouldRemoveStreamOnCancel() {
    // given
    final String jobType = "testJobOnCancel";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");
    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequest(jobType, WORKER, timeout, fetchVariables);
    assertThat(jobStreamer.containsStreamFor(jobType)).isTrue();

    // when
    streamObserver.cancel();

    // then
    Awaitility.await("until the stream is removed")
        .until(() -> !jobStreamer.containsStreamFor(jobType));
  }

  @Test
  public void shouldRejectWhenTypeIsEmpty() {
    // given
    final String jobType = "";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    // when
    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequestUnblocking(jobType, WORKER, timeout, fetchVariables);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0).getMessage())
        .isEqualTo(
            "INVALID_ARGUMENT: Expected to stream activated jobs with type to be present, but it was blank");
  }

  @Test
  public void shouldRejectWhenTypeIsBlank() {
    // given
    final String jobType = "   "; // string with only whitespaces
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    // when
    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequestUnblocking(jobType, WORKER, timeout, fetchVariables);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0).getMessage())
        .isEqualTo(
            "INVALID_ARGUMENT: Expected to stream activated jobs with type to be present, but it was blank");
  }

  @Test
  public void shouldRejectWhenTimeoutIsLessThanOne() {
    // given
    final String jobType = "testJob";
    final Duration timeout = Duration.ofMinutes(0);
    final List<String> fetchVariables = List.of("foo");

    // when
    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequestUnblocking(jobType, WORKER, timeout, fetchVariables);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0).getMessage())
        .isEqualTo(
            "INVALID_ARGUMENT: Expected to stream activated jobs with timeout to be greater than zero, but it was 0");
  }

  @Test
  public void shouldFailWhenStreamCannotBeAdded() {
    // given
    final var jobType = "testJobOnCancel";
    final var timeout = Duration.ofMinutes(1);
    final var fetchVariables = List.of("foo");
    final var failure = new RuntimeException("failed");
    jobStreamer.setFailOnAdd(failure);

    // when
    final var streamObserver =
        getStreamActivatedJobsRequest(jobType, WORKER, timeout, fetchVariables, false);

    // then
    Awaitility.await("until the stream is closed").until(streamObserver::isClosed);
    assertThat(streamObserver.getErrors())
        .hasSize(1)
        .first()
        .isInstanceOf(StatusRuntimeException.class)
        .asInstanceOf(InstanceOfAssertFactories.throwable(StatusRuntimeException.class))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getCode)
        .isEqualTo(Status.UNAVAILABLE.getCode());
  }

  private TestStreamObserver getStreamActivatedJobsRequestUnblocking(
      final String jobType,
      final String worker,
      final Duration timeout,
      final List<String> fetchVariables) {
    return getStreamActivatedJobsRequest(jobType, worker, timeout, fetchVariables, false);
  }

  private TestStreamObserver getStreamActivatedJobsRequest(
      final String jobType,
      final String worker,
      final Duration timeout,
      final List<String> fetchVariables) {
    return getStreamActivatedJobsRequest(jobType, worker, timeout, fetchVariables, true);
  }

  private TestStreamObserver getStreamActivatedJobsRequest(
      final String jobType,
      final String worker,
      final Duration timeout,
      final List<String> fetchVariables,
      final boolean waitStreamToBeAvailable) {
    final var streamObserver = new TestStreamObserver();
    final var request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(worker)
            .setTimeout(timeout.toMillis())
            .addAllFetchVariable(fetchVariables)
            .build();

    asyncClient.streamActivatedJobs(request, streamObserver);
    if (waitStreamToBeAvailable) {
      jobStreamer.waitStreamToBeAvailable(BufferUtil.wrapString(jobType));
    }

    return streamObserver;
  }
}
