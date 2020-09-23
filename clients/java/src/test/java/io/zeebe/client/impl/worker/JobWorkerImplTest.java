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
package io.zeebe.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.worker.JobHandler;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JobWorkerImplTest {

  private static final JobHandler NOOP_JOB_HANDLER = (client, job) -> {};
  private static final long SLOW_POLL_DELAY_IN_MS = 1_000L;
  private static final Duration SLOW_POLL_THRESHOLD = Duration.ofMillis(SLOW_POLL_DELAY_IN_MS / 2);

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private MockedGateway gateway;
  private ZeebeClient client;

  @Before
  public void setup() throws IOException {
    // Generate a unique in-process server name.
    final String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    gateway = new MockedGateway();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(gateway)
            .build()
            .start());

    // Create a client channel and register for automatic graceful shutdown.
    final ManagedChannel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    // Create the Zeebe Client
    client =
        new ZeebeClientImpl(new ZeebeClientBuilderImpl(), channel, GatewayGrpc.newStub(channel));
  }

  @Test
  public void shouldBackoffWhenGatewayRespondsWithResourceExhausted() {
    // given a gateway that responds with some jobs
    gateway.respondWith(TestData.jobs(10));

    // and a client with retry delay supplier that is slowing down polling
    client
        .newWorker()
        .jobType("test")
        .handler(NOOP_JOB_HANDLER)
        .backoffSupplier(prev -> SLOW_POLL_DELAY_IN_MS)
        .open();

    // and assuming that the gateway responded multiple times successfully with jobs
    gateway.startMeasuring();
    Awaitility.await()
        .pollInterval(Duration.ofMillis(10))
        .atMost(Duration.ofSeconds(1))
        .until(() -> gateway.getCountedPolls() > 3);
    gateway.stopMeasuring();

    // then polling is fast
    assertThat(gateway.getTimeBetweenLatestPolls()).isLessThan(SLOW_POLL_THRESHOLD);

    // when the gateway responds with errors
    gateway.respondWith(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));

    // then polling is slowed down
    gateway.startMeasuring();
    Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(gateway.getTimeBetweenLatestPolls()).isGreaterThan(SLOW_POLL_THRESHOLD));
  }

  /**
   * This mocked gateway is able to record metrics on polling for new jobs and easily switch how it
   * responds to polling.
   *
   * <ul>
   *   Due to the concurrent nature of the test setup and the job worker, 2 lock objects are used:
   *   <li>responsesLock to lock access to the mocking of responses objects for test setup;
   *   <li>metricsLock to lock access to the polling metrics objects.
   * </ul>
   */
  private static final class MockedGateway extends GatewayImplBase {

    // mocking of poll responses
    private final Object responsesLock = new Object();
    private boolean isInErrorMode = false;
    private ActivateJobsResponse pollSuccessResponse = ActivateJobsResponse.newBuilder().build();
    private StatusRuntimeException pollErrorResponse = new StatusRuntimeException(Status.UNKNOWN);

    // polling metrics
    private final Object metricsLock = new Object();
    private boolean isMeasuring = false;
    private long countedPolls = 0;
    private Instant lastPoll = null;
    private Duration timeBetweenLatestPolls = null;

    @Override
    public void activateJobs(
        final ActivateJobsRequest request,
        final StreamObserver<ActivateJobsResponse> responseObserver) {
      synchronized (metricsLock) {
        if (isMeasuring) {
          final Instant now = Instant.now();
          countedPolls++;
          if (lastPoll != null) {
            timeBetweenLatestPolls = Duration.between(lastPoll, now);
          }
          lastPoll = now;
        }
      }
      synchronized (responsesLock) {
        if (isInErrorMode) {
          responseObserver.onError(pollErrorResponse);
        } else {
          responseObserver.onNext(pollSuccessResponse);
          responseObserver.onCompleted();
        }
      }
    }

    public void respondWith(final List<ActivatedJob> jobs) {
      synchronized (responsesLock) {
        System.out.println("Now responding with jobs");
        isInErrorMode = false;
        pollSuccessResponse = ActivateJobsResponse.newBuilder().addAllJobs(jobs).build();
      }
    }

    public void respondWith(final StatusRuntimeException throwable) {
      synchronized (responsesLock) {
        System.out.println("Now responding exceptionally");
        isInErrorMode = true;
        pollErrorResponse = throwable;
      }
    }

    public void startMeasuring() {
      synchronized (metricsLock) {
        countedPolls = 0;
        lastPoll = null;
        timeBetweenLatestPolls = null;
        isMeasuring = true;
      }
    }

    public void stopMeasuring() {
      synchronized (metricsLock) {
        isMeasuring = false;
      }
    }

    public Duration getTimeBetweenLatestPolls() {
      synchronized (metricsLock) {
        return timeBetweenLatestPolls;
      }
    }

    public long getCountedPolls() {
      synchronized (metricsLock) {
        return countedPolls;
      }
    }
  }

  private static final class TestData {
    private static GatewayOuterClass.ActivatedJob job() {
      return GatewayOuterClass.ActivatedJob.newBuilder()
          .setKey(12)
          .setType("foo")
          .setWorkflowInstanceKey(123)
          .setBpmnProcessId("test1")
          .setWorkflowDefinitionVersion(2)
          .setWorkflowKey(23)
          .setElementId("foo")
          .setElementInstanceKey(23213)
          .setCustomHeaders("{\"version\": \"1\"}")
          .setWorker("worker1")
          .setRetries(34)
          .setDeadline(1231)
          .setVariables("{\"key\": \"val\"}")
          .build();
    }

    public static List<ActivatedJob> jobs(final int numberOfJobs) {
      return IntStream.range(0, numberOfJobs).mapToObj(i -> job()).collect(Collectors.toList());
    }
  }
}
