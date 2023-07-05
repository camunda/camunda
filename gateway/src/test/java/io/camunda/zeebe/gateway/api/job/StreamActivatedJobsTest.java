/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Test;

public class StreamActivatedJobsTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final String jobType = "testJob";
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = Arrays.asList("foo", "bar");

    final List<ActivatedJob> streamedJobs =
        getStreamActivatedJobsRequest(jobType, worker, timeout, fetchVariables).streamedJobs;

    assertThat(streamedJobs).isEmpty();

    // when
    final ActivatedJobImpl activatedJob = new ActivatedJobImpl();
    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType(jobType);
    final Map<String, Object> fetchedVariables = Map.of("foo", 1, "bar", 2);
    jobRecord.setVariables(MsgPackUtil.asMsgPack(fetchedVariables));
    activatedJob.setRecord(jobRecord);

    jobStreamer.push(activatedJob).join();

    // then
    final ActivatedJob streamedActivatedJob = streamedJobs.get(0);
    assertThat(streamedActivatedJob.getType()).isEqualTo(activatedJob.jobRecord().getType());
    assertThat(activatedJob.jobRecord().getVariables()).isEqualTo(fetchedVariables);
  }

  @Test
  public void shouldPushNoJobsWhenNonAvailable() {
    // given
    final String jobType1 = "testJob1";
    final String jobType2 = "testJob2";
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    final List<ActivatedJob> streamedJobs1 =
        getStreamActivatedJobsRequest(jobType1, worker, timeout, fetchVariables).streamedJobs;
    getStreamActivatedJobsRequest(jobType2, worker, timeout, fetchVariables);

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
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");

    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequest(jobType, worker, timeout, fetchVariables);

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
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(1);
    final List<String> fetchVariables = List.of("foo");
    final TestStreamObserver streamObserver =
        getStreamActivatedJobsRequest(jobType, worker, timeout, fetchVariables);
    assertThat(jobStreamer.containsStreamFor(jobType)).isTrue();

    // when
    streamObserver.cancel();

    // then
    Awaitility.await("until the stream is removed")
        .until(() -> !jobStreamer.containsStreamFor(jobType));
  }

  // TODO add test for onClose
  // How to test onClose? it seems the graceful way to close a stream from the client side is
  // simply to cancel it, as per the gRPC docs. So is onClose only for when we close it server side?

  private TestStreamObserver getStreamActivatedJobsRequest(
      final String jobType,
      final String worker,
      final Duration timeout,
      final List<String> fetchVariables) {
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(worker)
            .setTimeout(timeout.toMillis())
            .addAllFetchVariable(fetchVariables)
            .build();

    final List<ActivatedJob> streamedJobs = new ArrayList<>();
    final TestStreamObserver streamObserver = new TestStreamObserver(streamedJobs);
    asyncClient.streamActivatedJobs(request, streamObserver);
    jobStreamer.waitStreamToBeAvailable(BufferUtil.wrapString(jobType));

    return streamObserver;
  }

  private static class TestStreamObserver
      implements ClientResponseObserver<StreamActivatedJobsRequest, ActivatedJob> {
    private ClientCallStreamObserver<StreamActivatedJobsRequest> requestStream;
    private final List<ActivatedJob> streamedJobs;

    public TestStreamObserver(final List<ActivatedJob> streamedJobs) {
      this.streamedJobs = streamedJobs;
    }

    @Override
    public void onNext(final ActivatedJob value) {
      streamedJobs.add(value);
    }

    @Override
    public void onError(final Throwable t) {
      requestStream.onError(t);
    }

    @Override
    public void onCompleted() {}

    @Override
    public void beforeStart(
        final ClientCallStreamObserver<StreamActivatedJobsRequest> requestStream) {
      this.requestStream = requestStream;
    }

    public void cancel() {
      requestStream.cancel("test cancel", new RuntimeException());
    }
  }
}
