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
package io.camunda.client.impl.worker;

import static io.camunda.client.impl.CamundaClientEnvironmentVariables.CAMUNDA_CLIENT_WORKER_STREAM_ENABLED;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Uninterruptibles;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.client.impl.util.Environment;
import io.camunda.client.impl.util.EnvironmentExtension;
import io.camunda.client.impl.util.JobWorkerExecutors;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
import org.mockito.Mockito;

@SuppressWarnings("resource")
@ExtendWith({ExternalResourceSupport.class, EnvironmentExtension.class})
final class JobWorkerImplTest {

  private static final JobHandler NOOP_JOB_HANDLER = (client, job) -> {};
  private static final long SLOW_POLL_DELAY_IN_MS = 1_000L;
  // keeps the keys of pushed jobs apart from those of the polled ones
  private static final long STREAMED_JOB_KEY_OFFSET = 100L;
  private static final Duration SLOW_POLL_THRESHOLD = Duration.ofMillis(SLOW_POLL_DELAY_IN_MS / 2);
  private static final int MAX_JOBS_ACTIVE = 4;
  private static final long POLL_INTERVAL_IN_MS = 50L;
  private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);
  // A clock frozen in time: a job measured against a positive timeout is always still within it.
  private static final LongSupplier WITHIN_ACTIVATION = () -> 0L;

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private MockedGateway gateway;
  private CamundaClient client;
  private ManagedChannel channel;

  // Keep this explicit teardown so the client closes before the migrated JUnit 4 GrpcCleanupRule
  // checks that its channel has been released; @AutoClose runs too late for this mixed test.
  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @BeforeEach
  void setup() throws IOException {
    gateway = new MockedGateway();

    // ensure all gRPC resources are registered for cleanup. Since clients identify the in-process
    // server by its name, these names should be unique. gRPC advocates this in its test examples:
    // see https://github.com/grpc/grpc-java/tree/v1.35.0/examples/src/test/java/io/grpc/examples
    final String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(gateway)
            .build()
            .start());
    channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    client =
        new CamundaClientImpl(
            new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
            channel,
            GatewayGrpc.newStub(channel));
  }

  @Test
  void shouldBackoffWhenGatewayRespondsWithResourceExhausted() {
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

  @Test
  void shouldBackoffWhenStreamEnabledOnPollSuccessAndResponseIsEmpty() {
    // given a gateway that responds with some jobs
    gateway.respondWith(TestData.jobs(0));

    // and a client with stream enabled and a stream no jobs backoff supplier that is slowing down
    // polling
    client
        .newWorker()
        .jobType("test")
        .handler(NOOP_JOB_HANDLER)
        .streamNoJobsBackoffSupplier(prev -> SLOW_POLL_DELAY_IN_MS)
        .streamEnabled(true)
        .open();

    // and assuming that the gateway responded multiple times successfully
    gateway.startMeasuring();
    Awaitility.await()
        .pollInterval(Duration.ofMillis(10))
        .atMost(Duration.ofSeconds(5))
        .until(() -> gateway.getCountedPolls() > 3);
    gateway.stopMeasuring();

    // since stream is enabled then we expect the poll to backoff
    assertThat(gateway.getTimeBetweenLatestPolls()).isGreaterThan(SLOW_POLL_THRESHOLD);
  }

  @Test
  void shouldOpenStreamIfOptedIn() {
    // given
    final JobWorkerBuilderStep3 builder =
        client.newWorker().jobType("test").handler(NOOP_JOB_HANDLER).streamEnabled(true);

    // when
    try (final JobWorker ignored = builder.open()) {
      // then
      Awaitility.await("until a stream is open")
          .pollInterval(Duration.ofMillis(100))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(gateway.openStreams).hasSize(1));
    }
  }

  @Test
  void workerBuilderShouldOverrideEnvVariables() {
    // given
    Environment.system().put(CAMUNDA_CLIENT_WORKER_STREAM_ENABLED, "false");

    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    try (final CamundaClient configurationClient =
            builder.applyEnvironmentVariableOverrides(true).build();
        final CamundaClient camundaClient =
            new CamundaClientImpl(builder, channel, GatewayGrpc.newStub(channel))) {
      final JobWorkerBuilderStep3 jobWorkerBuilderStep3 =
          camundaClient.newWorker().jobType("test").handler(NOOP_JOB_HANDLER).streamEnabled(true);

      // when
      try (final JobWorker ignored = jobWorkerBuilderStep3.open()) {
        // then
        Awaitility.await("until a stream is open")
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(gateway.openStreams).hasSize(1));
      }
    }
  }

  @Test
  void shouldHandleOnlyCapacity() {
    // given
    final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    final ArrayList<io.camunda.client.api.response.ActivatedJob> jobs = new ArrayList<>();
    final CountDownLatch latch = new CountDownLatch(1);

    try (final CamundaClient client =
        new CamundaClientImpl(
            new CamundaClientBuilderImpl(),
            channel,
            GatewayGrpc.newStub(channel),
            new JobWorkerExecutors(executor, true))) {
      try (final JobWorker jobWorker =
          client
              .newWorker()
              .jobType("t")
              .handler(
                  (c, j) -> {
                    jobs.add(j);
                    Uninterruptibles.awaitUninterruptibly(latch);
                  })
              .pollInterval(Duration.ofHours(1))
              .maxJobsActive(1)
              .timeout(Duration.ofSeconds(5))
              .streamEnabled(true)
              .open()) {

        Awaitility.await("We need to wait until the streams have been opened")
            .until(() -> !gateway.openStreams.isEmpty());

        // when
        new Thread(() -> gateway.pushJobs(TestData.jobs(2))).start();
        Awaitility.await("Handler blocks after one").until(() -> jobs, Matchers.hasSize(1));
        latch.countDown();

        // then
        Awaitility.await("Handler should see both").until(() -> jobs, Matchers.hasSize(2));
      }
    }
  }

  @Test
  void shouldKeepPollingAfterHandlerExecutorRejectsJobs() {
    // given a worker whose handler executor can run a single job and rejects the rest, so that
    // most of an activated batch never reaches a handler
    final int maxJobsActive = 4;
    final AtomicInteger rejectedJobs = new AtomicInteger();
    final AtomicInteger handledJobs = new AtomicInteger();
    final CountDownLatch releaseHandler = new CountDownLatch(1);
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            (rejected, executor) -> {
              rejectedJobs.incrementAndGet();
              throw new RejectedExecutionException("Job handling executor is saturated");
            });
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(
                    (c, job) -> {
                      if (handledJobs.incrementAndGet() == 1) {
                        Uninterruptibles.awaitUninterruptibly(releaseHandler);
                      }
                    })
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .open()) {

      try {
        // when the executor rejects the rest of the activated batch
        Awaitility.await("Executor should reject the jobs it cannot run")
            .untilAtomic(rejectedJobs, Matchers.greaterThanOrEqualTo(maxJobsActive - 1));
      } finally {
        // and the handler capacity is free again, also when the check above failed: a handler left
        // waiting keeps its thread alive and holds up closing the client for 15 seconds
        releaseHandler.countDown();
      }

      // then the worker keeps activating jobs
      Awaitility.await("Worker should activate jobs again once capacity is free")
          .atMost(Duration.ofSeconds(10))
          .untilAtomic(handledJobs, Matchers.greaterThan(maxJobsActive));
    }
  }

  @Test
  void shouldKeepPollingWhileALongRunningJobHoldsPartOfTheCapacity() {
    // given a worker whose handler executor can run a single job and rejects the rest
    final int maxJobsActive = 4;
    final AtomicInteger rejectedJobs = new AtomicInteger();
    final CountDownLatch releaseHandler = new CountDownLatch(1);
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            (rejected, executor) -> {
              rejectedJobs.incrementAndGet();
              throw new RejectedExecutionException("Job handling executor is saturated");
            });
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler((c, job) -> Uninterruptibles.awaitUninterruptibly(releaseHandler))
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .open()) {
      try {
        // when one job occupies the handler and the rest of the batch is rejected
        Awaitility.await("Executor should reject the jobs it cannot run")
            .untilAtomic(rejectedJobs, Matchers.greaterThanOrEqualTo(maxJobsActive - 1));

        // then the worker keeps asking for jobs to fill the capacity the rejected jobs gave back,
        // rather than waiting for the one running job to finish
        gateway.startMeasuring();
        Awaitility.await("Worker should keep activating jobs while one job is still running")
            .atMost(Duration.ofSeconds(10))
            .until(() -> gateway.getCountedPolls() > 1);
      } finally {
        releaseHandler.countDown();
      }
    }
  }

  @Test
  void shouldNotAskForMoreJobsThanItCanRunWhenAJobRunsAndIsRefusedAtTheSameTime() {
    // given a worker whose handler executor runs a job and then reports it as refused
    final int maxJobsActive = 3;
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = new RunsThenRefusesExecutor();
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(NOOP_JOB_HANDLER)
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .open()) {

      // when the worker has been through several rounds of activating those jobs
      Awaitility.await("Worker should activate jobs repeatedly")
          .atMost(Duration.ofSeconds(10))
          .until(() -> gateway.getRequestedJobCounts().size() >= 3);

      // then it never asks for more jobs than it is allowed to run at a time, which it would do if
      // it counted a job that both ran and was refused as two free slots instead of one
      assertThat(gateway.getRequestedJobCounts())
          .allSatisfy(requested -> assertThat(requested).isLessThanOrEqualTo(maxJobsActive));
    }
  }

  @Test
  void shouldAskOnlyForTheCapacityThatPushedJobsLeave() {
    // given a worker whose capacity is partly taken by jobs the broker pushed to it
    final int maxJobsActive = 4;
    final int pushedJobs = 3;
    final AtomicInteger runningJobs = new AtomicInteger();
    final CountDownLatch releaseHandlers = new CountDownLatch(1);
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newFixedThreadPool(maxJobsActive);

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(
                    (c, job) -> {
                      runningJobs.incrementAndGet();
                      Uninterruptibles.awaitUninterruptibly(releaseHandlers);
                    })
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .streamEnabled(true)
                .open()) {

      try {
        Awaitility.await("Stream should be open").until(() -> !gateway.openStreams.isEmpty());
        pushJobsInBackground(pushedJobs);
        Awaitility.await("Pushed jobs should occupy the worker")
            .untilAtomic(runningJobs, Matchers.is(pushedJobs));

        // when the worker polls again
        // then it asks only for the jobs it can still run. Its own count of activated jobs says
        // every slot is free, since the jobs holding them were pushed and never counted.
        Awaitility.await("Worker should ask for its free capacity only")
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(
                () -> assertThat(lastRequestedJobCount()).isEqualTo(maxJobsActive - pushedJobs));
      } finally {
        // also when the check above failed: a handler left waiting keeps its thread alive and
        // holds up closing the client for 15 seconds
        releaseHandlers.countDown();
      }
    }
  }

  @Test
  void shouldNotAskForJobsWhileItCannotRunAnyMore() {
    // given a worker whose capacity is taken in full by jobs the broker pushed to it
    final int maxJobsActive = 2;
    final AtomicInteger runningJobs = new AtomicInteger();
    final CountDownLatch releaseHandlers = new CountDownLatch(1);
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newFixedThreadPool(maxJobsActive);

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(
                    (c, job) -> {
                      runningJobs.incrementAndGet();
                      Uninterruptibles.awaitUninterruptibly(releaseHandlers);
                    })
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .streamEnabled(true)
                .open()) {

      try {
        Awaitility.await("Stream should be open").until(() -> !gateway.openStreams.isEmpty());
        pushJobsInBackground(maxJobsActive);
        Awaitility.await("Pushed jobs should occupy the worker")
            .untilAtomic(runningJobs, Matchers.is(maxJobsActive));
        final int pollsBeforeItIsFull = gateway.getRequestedJobCounts().size();

        // when several poll intervals pass
        // then no request goes out, since every job it activated would go straight back
        Awaitility.await("Worker should stop asking for jobs it cannot run")
            .pollDelay(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(
                () ->
                    assertThat(gateway.getRequestedJobCounts())
                        .hasSizeLessThanOrEqualTo(pollsBeforeItIsFull + 1));
      } finally {
        releaseHandlers.countDown();
      }
    }
  }

  @Test
  void shouldHandBackAPolledJobWithoutWaitingForCapacity() {
    // given a worker that waits up to a job timeout for capacity, with most of its capacity taken
    // by jobs the broker pushed to it
    final int maxJobsActive = 4;
    final int pushedJobs = 3;
    final Duration jobTimeout = Duration.ofSeconds(30);
    final AtomicInteger runningJobs = new AtomicInteger();
    final CountDownLatch releaseHandlers = new CountDownLatch(1);
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newFixedThreadPool(maxJobsActive);

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(
                    (c, job) -> {
                      runningJobs.incrementAndGet();
                      Uninterruptibles.awaitUninterruptibly(releaseHandlers);
                    })
                .maxJobsActive(maxJobsActive)
                .timeout(jobTimeout)
                .pollInterval(Duration.ofMillis(50))
                .streamEnabled(true)
                .open()) {

      try {
        Awaitility.await("Stream should be open").until(() -> !gateway.openStreams.isEmpty());
        pushJobsInBackground(pushedJobs);
        Awaitility.await("Pushed jobs should occupy the worker")
            .untilAtomic(runningJobs, Matchers.is(pushedJobs));

        // when a poll brings back more jobs than the worker has capacity for, which happens when
        // the broker pushes a job while the response is on its way
        gateway.respondWith(TestData.jobs(maxJobsActive));

        // then the jobs it cannot run are handed back right away. Waiting for capacity would hold
        // on to the thread that carries the activation response for a job timeout per job, and
        // that thread carries the rest of the client's requests as well.
        Awaitility.await("Refused jobs should go back to the broker without waiting for capacity")
            .atMost(jobTimeout.dividedBy(3))
            .untilAsserted(
                () ->
                    assertThat(gateway.getFailedJobs())
                        .extracting(FailJobRequest::getJobKey)
                        .contains(1L, 2L, 3L));
      } finally {
        releaseHandlers.countDown();
      }
    }
  }

  @Test
  void shouldFailRejectedJobsBackToTheBroker() {
    // given a worker whose handler executor refuses every job
    final int maxJobsActive = 4;
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newSingleThreadExecutor();
    jobHandlingExecutor.shutdown();
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(NOOP_JOB_HANDLER)
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .open()) {

      // when the executor refuses the activated jobs
      // then they are handed back so another worker can pick them up right away
      Awaitility.await("Refused jobs should be failed back without using up a retry")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                final List<FailJobRequest> failedJobs = gateway.getFailedJobs();
                assertThat(failedJobs)
                    .extracting(FailJobRequest::getJobKey)
                    .contains(0L, 1L, 2L, 3L);
                assertThat(failedJobs)
                    .allSatisfy(
                        request -> {
                          assertThat(request.getRetries()).isEqualTo(TestData.JOB_RETRIES);
                          assertThat(request.getRetryBackOff()).isZero();
                        });
              });
    }
  }

  @Test
  void shouldLeaveARefusedStreamedJobToTheBroker() {
    // given a worker that streams jobs and whose handler executor refuses every job
    final int maxJobsActive = 4;
    final long streamedJobKey = 777L;
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newSingleThreadExecutor();
    jobHandlingExecutor.shutdown();
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(NOOP_JOB_HANDLER)
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .streamEnabled(true)
                .open()) {
      Awaitility.await("Stream should be open").until(() -> !gateway.openStreams.isEmpty());

      // when a streamed job is refused by the handler executor. The push runs the worker's handling
      // inline, so the job has been refused by the time this returns.
      gateway.pushJob(TestData.job(streamedJobKey));

      // then the worker keeps handing polled jobs back, so the fail path is demonstrably live
      Awaitility.await("Refused polled jobs should still be handed back")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> assertThat(gateway.getFailedJobs()).hasSizeGreaterThanOrEqualTo(maxJobsActive));

      // and the streamed job is left alone, because the broker yields a job whose push fails and a
      // fail command from here would race that yield
      assertThat(gateway.getFailedJobs())
          .extracting(FailJobRequest::getJobKey)
          .doesNotContain(streamedJobKey);
    }
  }

  @Test
  void shouldKeepPollingWhenHandingARefusedJobBackFails() {
    // given a job client that cannot send commands any more, as it would be while shutting down
    final JobClient brokenJobClient = Mockito.mock(JobClient.class);
    Mockito.when(
            brokenJobClient.newFailCommand(
                Mockito.any(io.camunda.client.api.response.ActivatedJob.class)))
        .thenThrow(new IllegalStateException("Client is shutting down"));

    // and a worker whose handler executor refuses every job
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            4,
            scheduler,
            Duration.ofMillis(50),
            brokenJobClient,
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            delay -> delay,
            JobWorkerMetrics.noop(),
            command -> {
              throw new RejectedExecutionException("The executor has no capacity");
            },
            WITHIN_ACTIVATION,
            JOB_TIMEOUT)) {

      // when the poller hands over two jobs and handing the first one back to the broker fails
      scheduler.tick(50, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(2));

      // then the worker still finished the poll, so it asks for jobs again
      scheduler.tick(50, TimeUnit.MILLISECONDS);
      assertThat(poller.getPollCount()).isEqualTo(2);
    }
  }

  @Test
  void shouldNotHandBackAJobWhoseHandlerAlreadyRan() {
    // given a worker whose handler executor runs a job and then reports it as refused
    final int maxJobsActive = 3;
    final AtomicInteger handledJobs = new AtomicInteger();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = new RunsThenRefusesExecutor();
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler((c, job) -> handledJobs.incrementAndGet())
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .open()) {

      // when the handler has run several rounds of jobs
      Awaitility.await("Handler should run the activated jobs")
          .atMost(Duration.ofSeconds(10))
          .untilAtomic(handledJobs, Matchers.greaterThan(maxJobsActive * 2));

      // then those jobs are left to the handler that ran them. Handing them back would have the
      // broker offer them again, so a job the handler already completed could be run a second time
      assertThat(gateway.getFailedJobs()).isEmpty();
    }
  }

  @Test
  void shouldBackOffWhenTheHandlerExecutorTakesNoJobAtAll() {
    // given a worker whose handler executor takes no job at all, so that nothing the worker
    // activates ever runs and nothing is left running to prompt the next poll
    final int maxJobsActive = 4;
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor = Executors.newSingleThreadExecutor();
    jobHandlingExecutor.shutdown();
    gateway.respondWith(TestData.jobs(maxJobsActive));

    try (final CamundaClient client =
            new CamundaClientImpl(
                new CamundaClientBuilderImpl().preferRestOverGrpc(false).build().getConfiguration(),
                channel,
                GatewayGrpc.newStub(channel),
                new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true));
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("test")
                .handler(NOOP_JOB_HANDLER)
                .maxJobsActive(maxJobsActive)
                .pollInterval(Duration.ofMillis(50))
                .backoffSupplier(prev -> SLOW_POLL_DELAY_IN_MS)
                .open()) {

      // when the worker keeps activating jobs the executor takes none of
      // then it slows down, rather than activating and handing back jobs as fast as it can
      gateway.startMeasuring();
      Awaitility.await("Worker should slow down its polling")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  assertThat(gateway.getTimeBetweenLatestPolls())
                      .isGreaterThan(SLOW_POLL_THRESHOLD));
    }
  }

  @Test
  void shouldCloseIfExecutorIsClosed() {
    // given
    final ScheduledExecutorService closedExecutor = Executors.newSingleThreadScheduledExecutor();

    try (final CamundaClient client =
        new CamundaClientImpl(
            new CamundaClientBuilderImpl(),
            channel,
            GatewayGrpc.newStub(channel),
            new JobWorkerExecutors(closedExecutor, true))) {

      final JobWorker jobWorker =
          client
              .newWorker()
              .jobType("t")
              .handler((c, j) -> {})
              .pollInterval(Duration.ofHours(1))
              .streamEnabled(true)
              .open();

      Awaitility.await("We need to wait until the streams have been opened")
          .until(() -> !gateway.openStreams.isEmpty());

      // when
      closedExecutor.shutdownNow();
      gateway.pushJob(TestData.job());

      // then
      Awaitility.await("Worker should be closed after detecting underlying executor is closed")
          .until(jobWorker::isClosed, Matchers.equalTo(true));
    }
  }

  @Test
  void shouldUseJobHandlingExecutorForJobs() {
    // given
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService jobHandlingExecutor =
        Mockito.spy(Executors.newSingleThreadExecutor(r -> new Thread(r, "test-executor-")));
    final JobWorkerExecutors executorResource =
        new JobWorkerExecutors(scheduler, true, jobHandlingExecutor, true);

    try (final CamundaClient client =
        new CamundaClientImpl(
            new CamundaClientBuilderImpl(),
            channel,
            GatewayGrpc.newStub(channel),
            executorResource)) {

      try (final JobWorker jobWorker =
          client
              .newWorker()
              .jobType("t")
              .handler(
                  (c, j) -> {
                    assertThat(Thread.currentThread().getName()).startsWith("test-executor-");
                  })
              .pollInterval(Duration.ofHours(1))
              .streamEnabled(true)
              .open()) {

        Awaitility.await("We need to wait until the streams have been opened")
            .until(() -> !gateway.openStreams.isEmpty());

        // when
        gateway.pushJob(TestData.job());

        // then
        Awaitility.await("Handler should be invoked")
            .untilAsserted(
                () ->
                    Mockito.verify(jobHandlingExecutor, Mockito.atLeastOnce())
                        .execute(Mockito.any(Runnable.class)));
      }
    }
  }

  @Test
  void shouldKeepPollingAfterAPollThrowsAnError() {
    // given a poller that throws an Error, as a protobuf gencode conflict does
    final ErrorThrowingJobPoller poller = new ErrorThrowingJobPoller();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    try (final JobWorkerImpl ignored =
        workerWith(
            scheduler,
            poller,
            new RecordingJobRunnableFactory(),
            WITHIN_ACTIVATION,
            Mockito.mock(JobClient.class))) {

      // when the worker runs on

      // then the worker polls again, because a lost Error stops it permanently
      Awaitility.await("a poll after the failed one")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(poller.getPollCount()).isGreaterThan(1));
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void shouldRunAJobThatIsStillWithinItsActivation() {
    // given a worker whose handler threads are free, so that a job starts as soon as it arrives
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();

    try (final JobWorkerImpl ignored =
        workerWith(scheduler, poller, handlers, WITHIN_ACTIVATION, Mockito.mock(JobClient.class))) {

      // when the poller hands over a job
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(1));

      // then its handler runs
      assertThat(handlers.getRanJobKeys()).containsExactly(0L);
    }
  }

  @Test
  void shouldNotRunAJobThatWaitedOutItsActivation() {
    // given a worker whose jobs wait for a free handler thread for longer than the timeout they
    // were activated with
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();

    try (final JobWorkerImpl ignored =
        workerWith(
            scheduler,
            poller,
            handlers,
            clockPastEveryActivation(),
            Mockito.mock(JobClient.class))) {

      // when the poller hands over a job
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(1));

      // then the handler does not run: the broker may have offered the job to another worker
      // already, so running it would do the same work twice and end in a rejected completion
      assertThat(handlers.getRanJobKeys()).isEmpty();
    }
  }

  @Test
  void shouldRunOnlyTheHeadOfABatchThatStartsWithinItsActivation() {
    // given real deadlines on a clock the test moves, rather than a fixed verdict, so that moving
    // the check away from the arrival point would show up here
    final TestNanoClock clock = new TestNanoClock();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final BusyHandlerThreads handlerThreads = new BusyHandlerThreads();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            MAX_JOBS_ACTIVE,
            scheduler,
            Duration.ofMillis(POLL_INTERVAL_IN_MS),
            Mockito.mock(JobClient.class),
            handlers,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            delay -> delay,
            JobWorkerMetrics.noop(),
            handlerThreads::execute,
            clock,
            JOB_TIMEOUT)) {

      // and a whole batch queued for a free handler thread
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(MAX_JOBS_ACTIVE));

      // when the first two get a thread straight away
      handlerThreads.runNext(2);

      // and the rest only get one after the activation they arrived with has run out
      clock.advance(JOB_TIMEOUT);
      handlerThreads.runQueued();

      // then the head ran and the tail was dropped, rather than running long after the broker gave
      // up on it
      assertThat(handlers.getRanJobKeys()).containsExactly(0L, 1L);
    }
  }

  @Test
  void shouldFreeTheCapacityOfAJobThatWaitedOutItsActivation() {
    // given a worker whose jobs all wait out their activation
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();

    try (final JobWorkerImpl ignored =
        workerWith(
            scheduler,
            poller,
            handlers,
            clockPastEveryActivation(),
            Mockito.mock(JobClient.class))) {

      // when a whole batch of them is dropped
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(MAX_JOBS_ACTIVE));

      // then the worker asks for a full batch again, so a dropped job never takes up capacity for
      // good, which is what would stop the worker from ever polling again
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      assertThat(poller.getPollCount()).isGreaterThan(1);
      assertThat(poller.getLastRequestedJobCount()).isEqualTo(MAX_JOBS_ACTIVE);
    }
  }

  @Test
  void shouldFreeTheCapacityOfAJobThatWaitedOutItsActivationOnlyOnce() {
    // given a worker whose executor reports every job as refused after taking it, which is what a
    // saturated pool with a caller-runs policy does while it is shutting down
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            MAX_JOBS_ACTIVE,
            scheduler,
            Duration.ofMillis(POLL_INTERVAL_IN_MS),
            Mockito.mock(JobClient.class),
            handlers,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            delay -> delay,
            JobWorkerMetrics.noop(),
            command -> {
              command.run();
              throw new RejectedExecutionException("Command ran here, but capacity ran out");
            },
            clockPastEveryActivation(),
            JOB_TIMEOUT)) {

      // when a whole batch is both dropped for having waited out its activation and reported as
      // refused, so that both paths are taken for the same job
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(MAX_JOBS_ACTIVE));

      // then each job gave its capacity back exactly once. Giving it back twice would have the
      // worker ask the broker for more jobs than it is allowed to run at a time.
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      assertThat(poller.getLastRequestedJobCount()).isEqualTo(MAX_JOBS_ACTIVE);
    }
  }

  @Test
  void shouldNotHandBackAJobThatWaitedOutItsActivation() {
    // given a worker whose jobs all wait out their activation
    final JobClient jobClient = Mockito.mock(JobClient.class);
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();

    try (final JobWorkerImpl ignored =
        workerWith(scheduler, poller, handlers, clockPastEveryActivation(), jobClient)) {

      // when a job is dropped
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      poller.handOverJobs(TestData.jobs(1));

      // then the worker does not fail it back to the broker. Its activation is over, so the job may
      // already belong to another worker, and failing it by key would take it away from them.
      Mockito.verify(jobClient, Mockito.never())
          .newFailCommand(Mockito.any(io.camunda.client.api.response.ActivatedJob.class));
    }
  }

  @Test
  void shouldFreeTheExecutorCapacityOfAStreamedJobThatWaitedOutItsActivation() {
    // given a worker whose handler threads are all busy, and the real executor, since it is the
    // executor rather than the worker that holds a pushed job's capacity
    final TestNanoClock clock = new TestNanoClock();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobRunnableFactory handlers = new RecordingJobRunnableFactory();
    final DeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final BusyHandlerThreads handlerThreads = new BusyHandlerThreads();
    final RecordingJobStreamer streamer = new RecordingJobStreamer();
    final BlockingExecutor executor =
        new BlockingExecutor(handlerThreads, MAX_JOBS_ACTIVE, JOB_TIMEOUT);

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            MAX_JOBS_ACTIVE,
            scheduler,
            Duration.ofMillis(POLL_INTERVAL_IN_MS),
            Mockito.mock(JobClient.class),
            handlers,
            poller,
            streamer,
            delay -> delay,
            delay -> delay,
            JobWorkerMetrics.noop(),
            executor,
            clock,
            JOB_TIMEOUT)) {

      // and a pushed job waiting for a handler thread, holding capacity the next poll is sized from
      streamer.push(STREAMED_JOB_KEY_OFFSET);
      assertThat(executor.freeCapacity()).isEqualTo(MAX_JOBS_ACTIVE - 1);

      // when a handler thread frees up and the job is dropped for having waited out its activation
      clock.advance(JOB_TIMEOUT);
      handlerThreads.runQueued();

      // then the capacity it held is back and the worker asks for a full batch. Nothing on the drop
      // path hands it back by name: a pushed job takes no capacity slot of the worker's own, so the
      // give-back is the executor's alone, and a leak here would shrink every later poll.
      assertThat(handlers.getRanJobKeys()).isEmpty();
      assertThat(executor.freeCapacity()).isEqualTo(MAX_JOBS_ACTIVE);
      scheduler.tick(POLL_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
      assertThat(poller.getLastRequestedJobCount()).isEqualTo(MAX_JOBS_ACTIVE);
    }
  }

  private JobWorkerImpl workerWith(
      final ScheduledExecutorService scheduler,
      final JobPoller poller,
      final JobRunnableFactory handlers,
      final LongSupplier nanoClock,
      final JobClient jobClient) {
    return new JobWorkerImpl(
        MAX_JOBS_ACTIVE,
        scheduler,
        Duration.ofMillis(POLL_INTERVAL_IN_MS),
        jobClient,
        handlers,
        poller,
        JobStreamer.noop(),
        delay -> delay,
        delay -> delay,
        JobWorkerMetrics.noop(),
        Runnable::run,
        nanoClock,
        JOB_TIMEOUT);
  }

  /**
   * A monotonic clock that jumps a whole timeout on every reading, so that every job it measures
   * has already waited out its activation by the time the handler would start, whatever moment the
   * check happens to fall on.
   */
  private static LongSupplier clockPastEveryActivation() {
    final AtomicLong nanos = new AtomicLong();
    return () -> nanos.getAndAdd(JOB_TIMEOUT.toNanos() + 1);
  }

  /**
   * Pushes jobs on the open stream from another thread, since a worker that has no capacity left
   * makes the pushing thread wait for it.
   */
  private void pushJobsInBackground(final int numberOfJobs) {
    new Thread(
            () -> {
              for (int i = 0; i < numberOfJobs; i++) {
                gateway.pushJob(TestData.job(STREAMED_JOB_KEY_OFFSET + i));
              }
            })
        .start();
  }

  private Integer lastRequestedJobCount() {
    final List<Integer> requestedJobCounts = gateway.getRequestedJobCounts();
    return requestedJobCounts.isEmpty()
        ? null
        : requestedJobCounts.get(requestedJobCounts.size() - 1);
  }

  /** Records the jobs whose handler ran, and reports each one as done straight away. */
  private static final class RecordingJobRunnableFactory implements JobRunnableFactory {
    private final List<Long> ranJobKeys = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Runnable create(
        final io.camunda.client.api.response.ActivatedJob job, final Runnable doneCallback) {
      return () -> {
        ranJobKeys.add(job.getKey());
        doneCallback.run();
      };
    }

    private List<Long> getRanJobKeys() {
      return ranJobKeys;
    }
  }

  /**
   * An executor that runs the command and then reports it as refused. A saturated {@link
   * java.util.concurrent.ThreadPoolExecutor} using {@link
   * java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy} behaves this way when it is shut down
   * while the caller is running the command.
   */
  private static final class RunsThenRefusesExecutor extends AbstractExecutorService {
    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(final Runnable command) {
      command.run();
      throw new RejectedExecutionException("Command ran here, but the executor is out of capacity");
    }
  }

  /**
   * A scheduler that runs tasks only when the test tells it to. {@link DeterministicScheduler}
   * refuses to answer whether it was shut down, while the worker asks that question whenever the
   * handler executor refuses a job, so the answer is supplied here.
   */
  private static final class AlwaysRunningDeterministicScheduler extends DeterministicScheduler {
    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }
  }

  /** Handler threads that are all busy until the test lets a command run. */
  private static final class BusyHandlerThreads implements Executor {
    private final Queue<Runnable> queued = new ArrayDeque<>();

    @Override
    public void execute(final Runnable command) {
      queued.add(command);
    }

    private void runNext(final int count) {
      for (int i = 0; i < count && !queued.isEmpty(); i++) {
        queued.poll().run();
      }
    }

    private void runQueued() {
      while (!queued.isEmpty()) {
        queued.poll().run();
      }
    }
  }

  /** A monotonic clock the test moves by hand, so that no test has to wait for real time. */
  private static final class TestNanoClock implements LongSupplier {
    private long nanos;

    @Override
    public long getAsLong() {
      return nanos;
    }

    private void advance(final Duration duration) {
      nanos += duration.toNanos();
    }
  }

  /** A stream the test pushes jobs onto, capturing the worker's handler as the real one would. */
  private static final class RecordingJobStreamer implements JobStreamer {
    private final JsonMapper jsonMapper = new CamundaObjectMapper();
    private final AtomicReference<Consumer<io.camunda.client.api.response.ActivatedJob>> consumer =
        new AtomicReference<>();

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void openStreamer(
        final Consumer<io.camunda.client.api.response.ActivatedJob> jobConsumer) {
      consumer.set(jobConsumer);
    }

    private void push(final long jobKey) {
      consumer.get().accept(new ActivatedJobImpl(jsonMapper, TestData.job(jobKey)));
    }
  }

  /** Throws an {@link Error} on the first poll, and counts the polls. */
  private static final class ErrorThrowingJobPoller implements JobPoller {
    private final AtomicInteger pollCount = new AtomicInteger();

    @Override
    public void poll(
        final int maxJobsToActivate,
        final Consumer<io.camunda.client.api.response.ActivatedJob> jobConsumer,
        final IntConsumer doneCallback,
        final Consumer<Throwable> errorCallback,
        final BooleanSupplier openSupplier) {
      if (pollCount.incrementAndGet() == 1) {
        throw new NoSuchMethodError("simulated protobuf gencode/runtime version skew");
      }
    }

    private int getPollCount() {
      return pollCount.get();
    }
  }

  /**
   * Hands the jobs over the way the real poller does: from a callback of the request future rather
   * than from the call to {@link #poll}. Anything thrown while handing a job over therefore ends up
   * in that future, where nobody looks at it, instead of reaching the worker.
   */
  private static final class RecordingJobPoller implements JobPoller {
    private final JsonMapper jsonMapper = new CamundaObjectMapper();
    private final AtomicInteger pollCount = new AtomicInteger();
    private final AtomicInteger lastRequestedJobCount = new AtomicInteger();
    private final AtomicReference<Consumer<io.camunda.client.api.response.ActivatedJob>>
        jobConsumer = new AtomicReference<>();
    private final AtomicReference<IntConsumer> doneCallback = new AtomicReference<>();

    @Override
    public void poll(
        final int maxJobsToActivate,
        final Consumer<io.camunda.client.api.response.ActivatedJob> jobConsumer,
        final IntConsumer doneCallback,
        final Consumer<Throwable> errorCallback,
        final BooleanSupplier openSupplier) {
      pollCount.incrementAndGet();
      lastRequestedJobCount.set(maxJobsToActivate);
      this.jobConsumer.set(jobConsumer);
      this.doneCallback.set(doneCallback);
    }

    private int getPollCount() {
      return pollCount.get();
    }

    private int getLastRequestedJobCount() {
      return lastRequestedJobCount.get();
    }

    private void handOverJobs(final List<ActivatedJob> jobs) {
      CompletableFuture.completedFuture(jobs)
          .thenApply(
              activatedJobs -> {
                activatedJobs.forEach(
                    job -> jobConsumer.get().accept(new ActivatedJobImpl(jsonMapper, job)));
                doneCallback.get().accept(activatedJobs.size());
                return null;
              });
    }
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

    private final Map<StreamActivatedJobsRequest, StreamObserver<ActivatedJob>> openStreams =
        new HashMap<>();
    private final Object responsesLock = new Object();
    private boolean isInErrorMode = false;
    private ActivateJobsResponse pollSuccessResponse = ActivateJobsResponse.newBuilder().build();
    private StatusRuntimeException pollErrorResponse = new StatusRuntimeException(Status.UNKNOWN);

    private final Object requestedJobCountsLock = new Object();
    private final List<Integer> requestedJobCounts = new ArrayList<>();

    private final Object failedJobsLock = new Object();
    private final List<FailJobRequest> failedJobs = new ArrayList<>();

    private final Object metricsLock = new Object();
    private boolean isMeasuring = false;
    private long countedPolls = 0;
    private Instant lastPoll = null;
    private Duration timeBetweenLatestPolls = null;

    @Override
    public void activateJobs(
        final ActivateJobsRequest request,
        final StreamObserver<ActivateJobsResponse> responseObserver) {
      synchronized (requestedJobCountsLock) {
        requestedJobCounts.add(request.getMaxJobsToActivate());
      }
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

    @Override
    public void streamActivatedJobs(
        final StreamActivatedJobsRequest request,
        final StreamObserver<ActivatedJob> responseObserver) {
      final ServerCallStreamObserver<ActivatedJob> observer =
          (ServerCallStreamObserver<ActivatedJob>) responseObserver;
      openStreams.put(request, responseObserver);
      observer.setOnCancelHandler(() -> openStreams.remove(request));
      observer.setOnCloseHandler(() -> openStreams.remove(request));
    }

    public void respondWith(final List<ActivatedJob> jobs) {
      synchronized (responsesLock) {
        System.out.println("Now responding with jobs");
        isInErrorMode = false;
        pollSuccessResponse = ActivateJobsResponse.newBuilder().addAllJobs(jobs).build();
      }
    }

    public void pushJob(final ActivatedJob job) {
      openStreams.values().stream().findFirst().ifPresent((observer) -> observer.onNext(job));
    }

    public void pushJobs(final List<ActivatedJob> jobs) {
      openStreams.values().stream()
          .findFirst()
          .ifPresent((observer) -> jobs.forEach(observer::onNext));
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

    public List<Integer> getRequestedJobCounts() {
      synchronized (requestedJobCountsLock) {
        return new ArrayList<>(requestedJobCounts);
      }
    }

    @Override
    public void failJob(
        final FailJobRequest request, final StreamObserver<FailJobResponse> responseObserver) {
      synchronized (failedJobsLock) {
        failedJobs.add(request);
      }
      responseObserver.onNext(FailJobResponse.newBuilder().build());
      responseObserver.onCompleted();
    }

    public List<FailJobRequest> getFailedJobs() {
      synchronized (failedJobsLock) {
        return new ArrayList<>(failedJobs);
      }
    }
  }
}
