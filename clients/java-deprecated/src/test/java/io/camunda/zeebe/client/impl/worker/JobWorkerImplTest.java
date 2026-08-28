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
package io.camunda.zeebe.client.impl.worker;

import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.ZEEBE_CLIENT_WORKER_STREAM_ENABLED;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Uninterruptibles;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.client.impl.util.EnvironmentExtension;
import io.camunda.zeebe.client.impl.util.ExecutorResource;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
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
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private MockedGateway gateway;
  private ZeebeClient client;
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
        new ZeebeClientImpl(new ZeebeClientBuilderImpl(), channel, GatewayGrpc.newStub(channel));
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

    // and a client with stream enabled and retry delay supplier that is slowing down polling
    client
        .newWorker()
        .jobType("test")
        .handler(NOOP_JOB_HANDLER)
        .backoffSupplier(prev -> SLOW_POLL_DELAY_IN_MS)
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
    Environment.system().put(ZEEBE_CLIENT_WORKER_STREAM_ENABLED, "false");

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(true).build();
    final ZeebeClient zeebeClient =
        new ZeebeClientImpl(builder, channel, GatewayGrpc.newStub(channel));

    final JobWorkerBuilderStep3 jobWorkerBuilderStep3 =
        zeebeClient.newWorker().jobType("test").handler(NOOP_JOB_HANDLER).streamEnabled(true);

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
    final ArrayList<io.camunda.zeebe.client.api.response.ActivatedJob> jobs = new ArrayList<>();
    final CountDownLatch latch = new CountDownLatch(1);

    try (final ZeebeClient client =
        new ZeebeClientImpl(
            new ZeebeClientBuilderImpl(),
            channel,
            GatewayGrpc.newStub(channel),
            new ExecutorResource(executor, false))) {
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
    // given a worker whose handler executor can run a single job and refuses the rest, so that
    // most of an activated batch never reaches a handler
    final int maxJobsActive = 4;
    final AtomicInteger handledJobs = new AtomicInteger();
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final CapacityBoundExecutor jobExecutor = new CapacityBoundExecutor(1);

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            Mockito.mock(JobClient.class),
            (job, doneCallback) ->
                () -> {
                  handledJobs.incrementAndGet();
                  doneCallback.run();
                },
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            jobExecutor)) {

      // when the executor refuses the jobs it cannot run
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));
      assertThat(jobExecutor.getRefusedCount()).isEqualTo(maxJobsActive - 1);

      // and the handler capacity is free again
      jobExecutor.runTakenCommands();

      // then the worker activates and handles jobs again
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));
      jobExecutor.runTakenCommands();
      assertThat(handledJobs).hasValue(2);
    }
  }

  @Test
  void shouldKeepPollingWhileALongRunningJobHoldsPartOfTheCapacity() {
    // given a worker whose handler executor can run a single job and refuses the rest
    final int maxJobsActive = 4;
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final CapacityBoundExecutor jobExecutor = new CapacityBoundExecutor(1);

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            Mockito.mock(JobClient.class),
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            jobExecutor)) {

      // when one job occupies the handler and the rest of the batch is refused
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));
      assertThat(jobExecutor.getRefusedCount()).isEqualTo(maxJobsActive - 1);

      // then the worker asks for jobs again to fill the capacity the refused jobs gave back,
      // rather than waiting for the one job it is still running to finish
      tickToNextPoll(scheduler);
      assertThat(poller.getPollCount()).isEqualTo(2);
    }
  }

  @Test
  void shouldNotAskForMoreJobsThanItCanRunWhenAJobRunsAndIsRefusedAtTheSameTime() {
    // given a worker whose handler executor runs a job and then reports it as refused
    final int maxJobsActive = 3;
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            Mockito.mock(JobClient.class),
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            new RunsThenRefusesExecutor())) {

      // when the worker has been through several rounds of activating those jobs
      for (int round = 0; round < 3; round++) {
        tickToNextPoll(scheduler);
        poller.handOverJobs(TestData.jobs(maxJobsActive));
      }

      // then it never asks for more jobs than it is allowed to run at a time, which it would do if
      // it counted a job that both ran and was refused as two free slots instead of one
      assertThat(poller.getRequestedJobCounts())
          .hasSize(3)
          .allSatisfy(requested -> assertThat(requested).isLessThanOrEqualTo(maxJobsActive));
    }
  }

  @Test
  void shouldFailRejectedJobsBackToTheBroker() {
    // given a worker whose handler executor refuses every job
    final int maxJobsActive = 4;
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingFailJobClient jobClient = new RecordingFailJobClient();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            jobClient.client(),
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            new CapacityBoundExecutor(0))) {

      // when the executor refuses the activated jobs
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));

      // then they are handed back so another worker can pick them up right away
      assertThat(jobClient.getFailedJobKeys()).containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
      assertThat(jobClient.getRetries()).containsOnly(TestData.JOB_RETRIES);
      assertThat(jobClient.getRetryBackoffCount()).isZero();
    }
  }

  @Test
  void shouldLeaveARefusedStreamedJobToTheBroker() {
    // given a worker that streams jobs and whose handler executor refuses every job
    final int maxJobsActive = 4;
    final long streamedJobKey = 777L;
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingJobStreamer streamer = new RecordingJobStreamer();
    final RecordingFailJobClient jobClient = new RecordingFailJobClient();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            jobClient.client(),
            (job, doneCallback) -> doneCallback,
            poller,
            streamer,
            delay -> delay,
            JobWorkerMetrics.noop(),
            new CapacityBoundExecutor(0))) {

      // when a streamed job and a batch of polled jobs are both refused by the handler executor
      streamer.pushJob(TestData.job(streamedJobKey));
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));

      // then the polled jobs are handed back, so the fail path is demonstrably live
      assertThat(jobClient.getFailedJobKeys()).containsExactlyInAnyOrder(0L, 1L, 2L, 3L);

      // and the streamed job is left alone, because the broker yields a job whose push fails and a
      // fail command from here would race that yield
      assertThat(jobClient.getFailedJobKeys()).doesNotContain(streamedJobKey);
    }
  }

  @Test
  void shouldKeepPollingWhenHandingARefusedJobBackFails() {
    // given a job client that cannot send commands any more, as it would be while shutting down
    final JobClient brokenJobClient = Mockito.mock(JobClient.class);
    Mockito.when(
            brokenJobClient.newFailCommand(
                Mockito.any(io.camunda.zeebe.client.api.response.ActivatedJob.class)))
        .thenThrow(new IllegalStateException("Client is shutting down"));

    // and a worker whose handler executor refuses every job
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            4,
            scheduler,
            POLL_INTERVAL,
            brokenJobClient,
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            new CapacityBoundExecutor(0))) {

      // when the poller hands over two jobs and handing the first one back to the broker fails
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(2));

      // then the worker still finished the poll, so it asks for jobs again
      tickToNextPoll(scheduler);
      assertThat(poller.getPollCount()).isEqualTo(2);
    }
  }

  @Test
  void shouldNotHandBackAJobWhoseHandlerAlreadyRan() {
    // given a worker whose handler executor runs a job and then reports it as refused
    final int maxJobsActive = 3;
    final AtomicInteger handledJobs = new AtomicInteger();
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();
    final RecordingFailJobClient jobClient = new RecordingFailJobClient();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            jobClient.client(),
            (job, doneCallback) ->
                () -> {
                  handledJobs.incrementAndGet();
                  doneCallback.run();
                },
            poller,
            JobStreamer.noop(),
            delay -> delay,
            JobWorkerMetrics.noop(),
            new RunsThenRefusesExecutor())) {

      // when the handler has run several rounds of jobs
      for (int round = 0; round < 3; round++) {
        tickToNextPoll(scheduler);
        poller.handOverJobs(TestData.jobs(maxJobsActive));
      }
      assertThat(handledJobs).hasValue(maxJobsActive * 3);

      // then those jobs are left to the handler that ran them. Handing them back would have the
      // broker offer them again, so a job the handler already completed could be run a second time
      assertThat(jobClient.getFailedJobKeys()).isEmpty();
    }
  }

  @Test
  void shouldBackOffWhenTheHandlerExecutorTakesNoJobAtAll() {
    // given a worker whose handler executor takes no job at all, so that nothing the worker
    // activates ever runs and nothing is left running to prompt the next poll
    final int maxJobsActive = 4;
    final AlwaysRunningDeterministicScheduler scheduler = new AlwaysRunningDeterministicScheduler();
    final RecordingJobPoller poller = new RecordingJobPoller();

    try (final JobWorkerImpl ignored =
        new JobWorkerImpl(
            maxJobsActive,
            scheduler,
            POLL_INTERVAL,
            Mockito.mock(JobClient.class),
            (job, doneCallback) -> doneCallback,
            poller,
            JobStreamer.noop(),
            previousDelay -> SLOW_POLL_DELAY_IN_MS,
            JobWorkerMetrics.noop(),
            new CapacityBoundExecutor(0))) {

      // when the worker activates a batch the executor takes none of
      tickToNextPoll(scheduler);
      poller.handOverJobs(TestData.jobs(maxJobsActive));

      // then it does not poll again at the usual interval, it slows down instead of activating and
      // handing back jobs as fast as the broker can answer
      scheduler.tick(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(poller.getPollCount()).isEqualTo(1);

      scheduler.tick(SLOW_POLL_DELAY_IN_MS, TimeUnit.MILLISECONDS);
      assertThat(poller.getPollCount()).isEqualTo(2);
    }
  }

  @Test
  void shouldCloseIfExecutorIsClosed() {
    // given
    final ScheduledExecutorService closedExecutor = Executors.newSingleThreadScheduledExecutor();

    try (final ZeebeClient client =
        new ZeebeClientImpl(
            new ZeebeClientBuilderImpl(),
            channel,
            GatewayGrpc.newStub(channel),
            new ExecutorResource(closedExecutor, false))) {

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

  /** Runs whatever the worker scheduled for the next poll. */
  private static void tickToNextPoll(final DeterministicScheduler scheduler) {
    scheduler.tick(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * An executor that takes a fixed number of commands and refuses everything after that, the way a
   * saturated thread pool does. It holds on to the commands it took until the test runs them, so
   * that a test can decide when the handler capacity becomes free again.
   */
  private static final class CapacityBoundExecutor implements Executor {
    private final int capacity;
    private final List<Runnable> takenCommands = new CopyOnWriteArrayList<>();
    private final AtomicInteger refusedCommands = new AtomicInteger();

    private CapacityBoundExecutor(final int capacity) {
      this.capacity = capacity;
    }

    @Override
    public void execute(final Runnable command) {
      if (takenCommands.size() >= capacity) {
        refusedCommands.incrementAndGet();
        throw new RejectedExecutionException("Job handling executor is saturated");
      }
      takenCommands.add(command);
    }

    private void runTakenCommands() {
      final List<Runnable> commands = new ArrayList<>(takenCommands);
      takenCommands.clear();
      commands.forEach(Runnable::run);
    }

    private int getRefusedCount() {
      return refusedCommands.get();
    }
  }

  /**
   * Answers the fail command chain with mocks and records what the worker hands back, so that a
   * test can see which jobs went back to the broker without going through a gateway.
   */
  private static final class RecordingFailJobClient {
    private final JobClient jobClient = Mockito.mock(JobClient.class);
    private final List<Long> failedJobKeys = new CopyOnWriteArrayList<>();
    private final List<Integer> retries = new CopyOnWriteArrayList<>();
    private final AtomicInteger retryBackoffCount = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private RecordingFailJobClient() {
      final FailJobCommandStep1 failCommand = Mockito.mock(FailJobCommandStep1.class);
      final FailJobCommandStep2 failCommandWithRetries =
          Mockito.mock(FailJobCommandStep2.class, Mockito.RETURNS_SELF);
      Mockito.when(
              jobClient.newFailCommand(
                  Mockito.any(io.camunda.zeebe.client.api.response.ActivatedJob.class)))
          .thenAnswer(
              invocation -> {
                failedJobKeys.add(
                    invocation
                        .<io.camunda.zeebe.client.api.response.ActivatedJob>getArgument(0)
                        .getKey());
                return failCommand;
              });
      Mockito.when(failCommand.retries(Mockito.anyInt()))
          .thenAnswer(
              invocation -> {
                retries.add(invocation.getArgument(0));
                return failCommandWithRetries;
              });
      Mockito.when(failCommandWithRetries.retryBackoff(Mockito.any()))
          .thenAnswer(
              invocation -> {
                retryBackoffCount.incrementAndGet();
                return failCommandWithRetries;
              });
      Mockito.when(failCommandWithRetries.send()).thenReturn(Mockito.mock(ZeebeFuture.class));
    }

    private JobClient client() {
      return jobClient;
    }

    private List<Long> getFailedJobKeys() {
      return failedJobKeys;
    }

    private List<Integer> getRetries() {
      return retries;
    }

    private int getRetryBackoffCount() {
      return retryBackoffCount.get();
    }
  }

  /** Hands the worker jobs the way a push from the broker does. */
  private static final class RecordingJobStreamer implements JobStreamer {
    private final JsonMapper jsonMapper = new ZeebeObjectMapper();
    private final AtomicReference<Consumer<io.camunda.zeebe.client.api.response.ActivatedJob>>
        jobConsumer = new AtomicReference<>();

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void openStreamer(
        final Consumer<io.camunda.zeebe.client.api.response.ActivatedJob> jobConsumer) {
      this.jobConsumer.set(jobConsumer);
    }

    private void pushJob(final ActivatedJob job) {
      jobConsumer.get().accept(new ActivatedJobImpl(jsonMapper, job));
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
    private final JsonMapper jsonMapper = new ZeebeObjectMapper();
    private final AtomicInteger pollCount = new AtomicInteger();
    private final List<Integer> requestedJobCounts = new CopyOnWriteArrayList<>();
    private final AtomicReference<Consumer<io.camunda.zeebe.client.api.response.ActivatedJob>>
        jobConsumer = new AtomicReference<>();
    private final AtomicReference<IntConsumer> doneCallback = new AtomicReference<>();

    @Override
    public void poll(
        final int maxJobsToActivate,
        final Consumer<io.camunda.zeebe.client.api.response.ActivatedJob> jobConsumer,
        final IntConsumer doneCallback,
        final Consumer<Throwable> errorCallback,
        final BooleanSupplier openSupplier) {
      pollCount.incrementAndGet();
      requestedJobCounts.add(maxJobsToActivate);
      this.jobConsumer.set(jobConsumer);
      this.doneCallback.set(doneCallback);
    }

    private int getPollCount() {
      return pollCount.get();
    }

    private List<Integer> getRequestedJobCounts() {
      return requestedJobCounts;
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
