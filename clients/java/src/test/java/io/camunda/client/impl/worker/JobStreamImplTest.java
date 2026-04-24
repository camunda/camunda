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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.util.JsonUtil;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
import org.mockito.Mockito;

@ExtendWith(ExternalResourceSupport.class)
final class JobStreamImplTest {

  private static final String JOB_WORKER_LOGGER_NAME = "io.camunda.client.job.worker";

  @Rule
  public final GrpcCleanupRule grpcRule =
      new GrpcCleanupRule().setTimeout(1, TimeUnit.MILLISECONDS);

  private final Service service = new Service();
  private final DeterministicScheduler scheduler = new DeterministicScheduler();
  private JobClient client;
  private JobStreamerImpl jobStreamer;
  private ListAppender logCapture;

  @BeforeEach
  void beforeEach() throws IOException {
    final String name = InProcessServerBuilder.generateName();
    final ManagedChannel clientChannel =
        grpcRule.register(InProcessChannelBuilder.forName(name).directExecutor().build());
    final GatewayStub asyncStub = GatewayGrpc.newStub(clientChannel);

    grpcRule.register(
        InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start());
    client =
        new JobClientImpl(
            asyncStub,
            Mockito.mock(HttpClient.class),
            new CamundaClientBuilderImpl(),
            new CamundaObjectMapper(),
            ignored -> false);
    jobStreamer = createStreamer(Duration.ofHours(8));

    logCapture = new ListAppender("capture-" + JOB_WORKER_LOGGER_NAME);
    logCapture.start();
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration()
        .getLoggerConfig(JOB_WORKER_LOGGER_NAME)
        .addAppender(logCapture, null, null);
    ctx.updateLoggers();
  }

  @AfterEach
  void afterEach() {
    if (jobStreamer != null) {
      jobStreamer.close();
    }
    if (logCapture != null) {
      final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      ctx.getConfiguration()
          .getLoggerConfig(JOB_WORKER_LOGGER_NAME)
          .removeAppender(logCapture.getName());
      ctx.updateLoggers();
      logCapture.stop();
    }
  }

  @Test
  void shouldCancelStreamOnClose() {
    // given
    jobStreamer.openStreamer(ignored -> {});

    // when
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> registeredStream =
        service.lastStream();
    jobStreamer.close();

    // then
    assertThat(registeredStream.isCancelled()).isTrue();
  }

  @Test
  void shouldOpenStream() {
    // given
    final StreamActivatedJobsRequest expectedRequest =
        StreamActivatedJobsRequest.newBuilder()
            .setType("type")
            .setWorker("worker")
            .setTimeout(Duration.ofSeconds(10).toMillis())
            .addFetchVariable("foo")
            .addFetchVariable("bar")
            .addTenantIds("test-tenant")
            .build();

    // when
    jobStreamer.openStreamer(ignored -> {});

    // then
    assertThat(service.lastRequest()).isEqualTo(expectedRequest);
  }

  @Test
  void shouldForwardJobsToConsumer() {
    // given
    final List<ActivatedJob> jobs = new ArrayList<>();
    jobStreamer.openStreamer(jobs::add);

    // when
    service.pushJob();
    service.pushJob();

    // then
    assertThat(jobs).hasSize(2).extracting(ActivatedJob::getKey).containsExactly(0L, 1L);
  }

  @Test
  void shouldBackOffOnStreamError() {
    // given
    jobStreamer.openStreamer(ignored -> {});
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> lastStream =
        service.lastStream();

    // when - expire exactly the amount of time the stream would back off before opening
    lastStream.onError(new StatusRuntimeException(Status.ABORTED));
    scheduler.tick(10, TimeUnit.SECONDS);
    scheduler.runUntilIdle();

    // then
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> recreatedStream =
        service.lastStream();
    assertThat(recreatedStream).isNotEqualTo(lastStream);
    assertThat(recreatedStream.isCancelled()).isFalse();
  }

  @Test
  void shouldNotReopenStreamOnErrorIfClosed() {
    // given
    jobStreamer.openStreamer(ignored -> {});
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> lastStream =
        service.lastStream();

    // when
    jobStreamer.close();
    lastStream.onError(new StatusRuntimeException(Status.ABORTED));

    // avoid non-determinism by running all pending tasks
    scheduler.tick(1, TimeUnit.DAYS);
    scheduler.runUntilIdle();

    // then
    assertThat(service.streams).isEmpty();
  }

  @Test
  void shouldReopenStreamOnStreamingTimeout() {
    // given
    final Duration streamingTimeout = Duration.ofSeconds(2);
    jobStreamer = createStreamer(streamingTimeout);

    jobStreamer.openStreamer(ignored -> {});
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> initialStream =
        service.lastStream();

    // when
    scheduler.tick(streamingTimeout.toMillis(), TimeUnit.MILLISECONDS);
    scheduler.runUntilIdle();

    // then
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> recreatedStream =
        service.lastStream();
    assertThat(recreatedStream).isNotNull().isNotEqualTo(initialStream);
    assertThat(initialStream.isCancelled()).isTrue();
    assertThat(recreatedStream.isCancelled()).isFalse();
  }

  @Test
  void shouldLogProxy504AtDebugOnlyWithException() {
    // given - a realistic UNAVAILABLE status produced when an intermediary proxy (e.g. nginx)
    // terminates the idle streaming connection with HTTP 504.
    jobStreamer.openStreamer(ignored -> {});
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> lastStream =
        service.lastStream();

    // when
    lastStream.onError(
        new StatusRuntimeException(
            Status.UNAVAILABLE.withDescription(
                "HTTP status code 504\n"
                    + "invalid content-type: text/html\n"
                    + "headers: Metadata(:status=504,content-type=text/html)\n"
                    + "DATA-----------------------------\n"
                    + "<html><body>504 Gateway Time-out</body></html>")));
    scheduler.runUntilIdle();

    // then
    assertThat(eventsAt(Level.WARN)).as("proxy 504 should not produce a WARN").isEmpty();

    assertThat(eventsAt(Level.DEBUG))
        .as("DEBUG should carry the hint message and the full gRPC exception")
        .anySatisfy(
            e -> {
              assertThat(e.getMessage().getFormattedMessage())
                  .contains("upstream proxy")
                  .contains("504")
                  .contains("streamTimeout");
              assertThat(e.getThrown()).isInstanceOf(StatusRuntimeException.class);
            });
  }

  @Test
  void shouldLogNonProxyUnavailableAsWarnWithException() {
    // given - UNAVAILABLE for reasons other than an HTTP 504 from a proxy should continue to
    // surface the full stack trace at WARN.
    jobStreamer.openStreamer(ignored -> {});
    final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> lastStream =
        service.lastStream();

    // when
    lastStream.onError(
        new StatusRuntimeException(Status.UNAVAILABLE.withDescription("io exception")));
    scheduler.runUntilIdle();

    // then
    final List<LogEvent> warns = eventsAt(Level.WARN);
    assertThat(warns).hasSize(1);
    assertThat(warns.get(0).getThrown()).isInstanceOf(StatusRuntimeException.class);
    assertThat(warns.get(0).getMessage().getFormattedMessage()).contains("Failed to stream jobs");
  }

  private List<LogEvent> eventsAt(final Level level) {
    // ListAppender is attached to the nearest parent LoggerConfig (io.camunda.client), so filter
    // by exact logger name to avoid cross-contamination from sibling loggers.
    return logCapture.getEvents().stream()
        .filter(e -> JOB_WORKER_LOGGER_NAME.equals(e.getLoggerName()))
        .filter(e -> level.equals(e.getLevel()))
        .collect(Collectors.toList());
  }

  private JobStreamerImpl createStreamer(final Duration streamingTimeout) {
    return new JobStreamerImpl(
        client,
        "type",
        "worker",
        Duration.ofSeconds(10),
        Arrays.asList("foo", "bar"),
        Arrays.asList("test-tenant"),
        streamingTimeout,
        ignored -> 10_000L,
        scheduler);
  }

  private static final class Service extends GatewayImplBase {
    private final ArrayList<StreamActivatedJobsRequest> requests = new ArrayList<>();
    private final Map<
            StreamActivatedJobsRequest, ServerCallStreamObserver<GatewayOuterClass.ActivatedJob>>
        streams = new HashMap<>();
    private int keyGenerator;

    @Override
    public void streamActivatedJobs(
        final StreamActivatedJobsRequest request,
        final StreamObserver<GatewayOuterClass.ActivatedJob> responseObserver) {
      final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> stream =
          (ServerCallStreamObserver<GatewayOuterClass.ActivatedJob>) responseObserver;

      streams.put(request, stream);
      requests.add(request);

      stream.setOnCancelHandler(() -> streams.remove(request));
      stream.setOnCloseHandler(() -> streams.remove(request));
    }

    private StreamActivatedJobsRequest lastRequest() {
      return requests.get(requests.size() - 1);
    }

    private ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> lastStream() {
      return streams.get(lastRequest());
    }

    private void pushJob() {
      final StreamActivatedJobsRequest request = lastRequest();
      final StreamObserver<GatewayOuterClass.ActivatedJob> stream = streams.get(request);
      final Map<String, String> variables = new HashMap<>();
      request.getFetchVariableList().forEach(key -> variables.put(key, "value"));

      final GatewayOuterClass.ActivatedJob job =
          GatewayOuterClass.ActivatedJob.newBuilder()
              .setType(request.getType())
              .setDeadline(System.currentTimeMillis() + request.getTimeout())
              .setWorker(request.getWorker())
              .setVariables(JsonUtil.toJson(variables))
              .setTenantId("test-tenant")
              .setKey(keyGenerator++)
              .build();

      stream.onNext(job);
    }
  }
}
