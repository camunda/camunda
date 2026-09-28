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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.ClientProperties;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class JobHandlingExecutorTest {

  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

  private final Set<Thread> handlerThreads = ConcurrentHashMap.newKeySet();
  private final AtomicInteger handledJobs = new AtomicInteger();
  private final CountDownLatch releaseHandlers = new CountDownLatch(1);

  private Server server;
  private ManagedChannel channel;
  private CamundaClient client;

  @BeforeEach
  void setUp() throws IOException {
    final String serverName = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new JobActivatingGateway())
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    releaseHandlers.countDown();
    if (client != null) {
      client.close();
    }
    channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shouldRunJobHandlersOnVirtualThreadsByDefault() {
    // given
    client = newClient(builder());

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).allMatch(JobHandlingExecutorTest::isVirtual);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shouldKeepHandlingJobsWhileAnotherHandlerIsBlockedByDefault() {
    // given
    client = newClient(builder());
    final AtomicBoolean blockedOne = new AtomicBoolean();

    // when
    openWorker(
        (jobClient, job) -> {
          if (blockedOne.compareAndSet(false, true)) {
            releaseHandlers.await();
            return;
          }
          handledJobs.incrementAndGet();
        });

    // then
    awaitHandledJobs(10);
  }

  @Test
  void shouldInterruptBlockedJobHandlersWhenClosingClientByDefault() {
    // given
    client = newClient(builder());
    final CountDownLatch handlerStarted = new CountDownLatch(1);
    final AtomicBoolean interrupted = new AtomicBoolean();
    openWorker(
        (jobClient, job) -> {
          handlerStarted.countDown();
          try {
            releaseHandlers.await();
          } catch (final InterruptedException e) {
            interrupted.set(true);
          }
        });
    Awaitility.await().atMost(AWAIT_TIMEOUT).until(() -> handlerStarted.getCount() == 0);

    // when
    client.close();

    // then
    Awaitility.await().atMost(AWAIT_TIMEOUT).untilTrue(interrupted);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void shouldRunJobHandlersOnFixedPlatformThreadPoolWhenThreadsConfigured(final int threads) {
    // given
    final CamundaClientBuilderImpl builder = builder();
    builder.numJobWorkerExecutionThreads(threads);
    client = newClient(builder);

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(10);
    assertThat(handlerThreads).noneMatch(JobHandlingExecutorTest::isVirtual);
    assertThat(handlerThreads).hasSizeLessThanOrEqualTo(threads);
  }

  @Test
  void shouldTreatThreadsFromPropertiesAsConfigured() {
    // given
    final Properties properties = new Properties();
    properties.setProperty(ClientProperties.JOB_WORKER_EXECUTION_THREADS, "1");
    final CamundaClientBuilderImpl builder = builder();
    builder.withProperties(properties);
    client = newClient(builder);

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).hasSize(1).noneMatch(JobHandlingExecutorTest::isVirtual);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shouldKeepDefaultJobHandlingWhenCopyingUnconfiguredConfiguration() {
    // given
    client = newClient(new CamundaClientBuilderImpl().withConfiguration(builder()));

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).allMatch(JobHandlingExecutorTest::isVirtual);
  }

  @Test
  void shouldKeepConfiguredThreadsWhenCopyingConfiguration() {
    // given
    final CamundaClientBuilderImpl configured = builder();
    configured.numJobWorkerExecutionThreads(1);
    client = newClient(new CamundaClientBuilderImpl().withConfiguration(configured));

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).hasSize(1).noneMatch(JobHandlingExecutorTest::isVirtual);
  }

  @Test
  void shouldRunJobHandlersOnSchedulingExecutorWhenThreadsIsZero() {
    // given
    final ScheduledExecutorService schedulingExecutor =
        Executors.newSingleThreadScheduledExecutor(namedThreadFactory("test-scheduling"));
    final CamundaClientBuilderImpl builder = builder();
    builder.jobWorkerSchedulingExecutor(schedulingExecutor).numJobWorkerExecutionThreads(0);
    client = newClient(builder);

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).extracting(Thread::getName).containsOnly("test-scheduling");
  }

  @Test
  void shouldRunJobHandlersOnCustomExecutorOverConfiguredThreads() {
    // given
    final ExecutorService jobHandlingExecutor =
        Executors.newSingleThreadExecutor(namedThreadFactory("test-handling"));
    final CamundaClientBuilderImpl builder = builder();
    builder.numJobWorkerExecutionThreads(4).jobHandlingExecutor(jobHandlingExecutor);
    client = newClient(builder);

    // when
    openWorker(recordingHandler());

    // then
    awaitHandledJobs(3);
    assertThat(handlerThreads).extracting(Thread::getName).containsOnly("test-handling");
  }

  private static CamundaClientBuilderImpl builder() {
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.preferRestOverGrpc(false);
    return builder;
  }

  private CamundaClient newClient(final CamundaClientConfiguration configuration) {
    return new CamundaClientImpl(configuration, channel, GatewayGrpc.newStub(channel));
  }

  private void openWorker(final JobHandler handler) {
    client
        .newWorker()
        .jobType("test")
        .handler(handler)
        .maxJobsActive(4)
        .pollInterval(Duration.ofMillis(10))
        .open();
  }

  private JobHandler recordingHandler() {
    return (jobClient, job) -> {
      handlerThreads.add(Thread.currentThread());
      handledJobs.incrementAndGet();
    };
  }

  private void awaitHandledJobs(final int count) {
    Awaitility.await().atMost(AWAIT_TIMEOUT).until(() -> handledJobs.get() >= count);
  }

  private static ThreadFactory namedThreadFactory(final String name) {
    return runnable -> new Thread(runnable, name);
  }

  private static boolean isVirtual(final Thread thread) {
    try {
      return (Boolean) Thread.class.getMethod("isVirtual").invoke(thread);
    } catch (final NoSuchMethodException e) {
      return false;
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class JobActivatingGateway extends GatewayImplBase {
    private final AtomicLong nextJobKey = new AtomicLong();

    @Override
    public void activateJobs(
        final ActivateJobsRequest request,
        final StreamObserver<ActivateJobsResponse> responseObserver) {
      final ActivateJobsResponse.Builder response = ActivateJobsResponse.newBuilder();
      for (int i = 0; i < request.getMaxJobsToActivate(); i++) {
        response.addJobs(TestData.job(nextJobKey.incrementAndGet()));
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }
  }
}
