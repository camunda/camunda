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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Rule;
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
  private static final Duration SLOW_POLL_THRESHOLD = Duration.ofMillis(SLOW_POLL_DELAY_IN_MS / 2);

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private MockedGateway gateway;
  private CamundaClient client;
  private ManagedChannel channel;

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

    client.close();
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
    builder.applyEnvironmentVariableOverrides(true).build();
    final CamundaClient camundaClient =
        new CamundaClientImpl(builder, channel, GatewayGrpc.newStub(channel));

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
            })) {

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

  /**
   * Hands the jobs over the way the real poller does: from a callback of the request future rather
   * than from the call to {@link #poll}. Anything thrown while handing a job over therefore ends up
   * in that future, where nobody looks at it, instead of reaching the worker.
   */
  private static final class RecordingJobPoller implements JobPoller {
    private final JsonMapper jsonMapper = new CamundaObjectMapper();
    private final AtomicInteger pollCount = new AtomicInteger();
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
      this.jobConsumer.set(jobConsumer);
      this.doneCallback.set(doneCallback);
    }

    private int getPollCount() {
      return pollCount.get();
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
