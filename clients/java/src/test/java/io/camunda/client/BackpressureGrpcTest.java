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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.BackpressureProfile;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.backpressure.BackpressureManager;
import io.camunda.client.impl.backpressure.BackpressureManager.Permit;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs the client against a gateway stub that answers gRPC calls with {@code RESOURCE_EXHAUSTED},
 * and checks what the client does with it.
 */
final class BackpressureGrpcTest {

  private static final Metadata.Key<String> TENANT_HEADER =
      Metadata.Key.of("camunda-physical-tenant", Metadata.ASCII_STRING_MARSHALLER);

  private final AtomicInteger publishCalls = new AtomicInteger();
  private final AtomicInteger completeCalls = new AtomicInteger();
  private final AtomicInteger topologyCalls = new AtomicInteger();
  private final AtomicInteger activateCalls = new AtomicInteger();
  private final AtomicInteger completeRejections = new AtomicInteger();
  private final List<List<String>> tenantHeaders = new CopyOnWriteArrayList<>();

  /** Decides how the gateway answers the n-th publish call: {@code null} for success. */
  private volatile IntFunction<Status> publishOutcome = n -> null;

  private Server server;
  private ManagedChannel channel;
  private CamundaClientImpl client;

  @BeforeEach
  void startGateway() throws Exception {
    final String name = "backpressure-" + UUID.randomUUID();
    final ServerInterceptor recordHeaders =
        new ServerInterceptor() {
          @Override
          public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
              final ServerCall<ReqT, RespT> call,
              final Metadata headers,
              final ServerCallHandler<ReqT, RespT> next) {
            tenantHeaders.add(
                headers.getAll(TENANT_HEADER) == null
                    ? java.util.Collections.<String>emptyList()
                    : copy(headers));
            return next.startCall(call, headers);
          }

          private List<String> copy(final Metadata headers) {
            final List<String> values = new ArrayList<>();
            headers.getAll(TENANT_HEADER).forEach(values::add);
            return values;
          }
        };
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(ServerInterceptors.intercept(new Gateway(), recordHeaders))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
  }

  @AfterEach
  void stopGateway() throws Exception {
    if (client != null) {
      client.close();
    }
    channel.shutdownNow();
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private CamundaClientImpl newClient(final BackpressureProfile profile) {
    return newClient(new CamundaClientBuilderImpl().backpressureProfile(profile));
  }

  private CamundaClientImpl newClient(final CamundaClientBuilder builder) {
    ((CamundaClientBuilderImpl) builder).preferRestOverGrpc(false);
    client = new CamundaClientImpl((CamundaClientBuilderImpl) builder, channel);
    return client;
  }

  private static CamundaFuture<?> publish(final CamundaClient c) {
    return c.newPublishMessageCommand().messageName("order").correlationKey("1").send();
  }

  @Test
  void shouldRetryAfterTheGatewayRejectedBecauseOfBackpressure() {
    // given: the first two calls are rejected
    publishOutcome = n -> n <= 2 ? Status.RESOURCE_EXHAUSTED.withDescription("busy") : null;
    final CamundaClient c = newClient(BackpressureProfile.BALANCED);

    // when
    final io.camunda.client.api.response.PublishMessageResponse response =
        c.newPublishMessageCommand().messageName("order").correlationKey("1").send().join();

    // then
    assertThat(response.getMessageKey()).isEqualTo(42L);
    assertThat(publishCalls).hasValue(3);
  }

  @Test
  void shouldGiveUpAfterThreeAttemptsAndFailWithTheLastRejection() {
    // given
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED.withDescription("busy " + n);
    final CamundaClientImpl c = newClient(BackpressureProfile.BALANCED);

    // when / then
    assertThatThrownBy(() -> publish(c).join())
        .isInstanceOf(ClientStatusException.class)
        .satisfies(
            e ->
                assertThat(((ClientStatusException) e).getStatusCode())
                    .isEqualTo(Status.Code.RESOURCE_EXHAUSTED))
        .hasMessageContaining("busy 3");
    assertThat(publishCalls).hasValue(3);
    // and: every rejection counts for the limit, shared with the REST requests of the client
    assertThat(c.backpressure().manager().state().consecutive()).isEqualTo(3);
    assertThat(c.backpressure().manager().state().permitsMax()).isEqualTo(5);
    assertThat(c.backpressure().manager().state().permitsCurrent()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "UNAVAILABLE", "INVALID_ARGUMENT", "ABORTED"})
  void shouldNotRetryOtherStatuses(final String code) {
    // given
    final Status status = Status.Code.valueOf(code).toStatus();
    publishOutcome = n -> status;
    final CamundaClient c = newClient(BackpressureProfile.BALANCED);

    // when / then
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(1);
  }

  @Test
  void shouldNotTreatAMessageSizeLimitAsBackpressure() {
    // given
    publishOutcome =
        n -> Status.RESOURCE_EXHAUSTED.withDescription("gRPC message exceeds maximum size 4: 5");
    final CamundaClientImpl c = newClient(BackpressureProfile.BALANCED);

    // when / then
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(1);
    assertThat(c.backpressure().manager().state().consecutive()).isZero();
  }

  @Test
  void shouldKeepWithinTheRequestTimeout() {
    // given
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED;
    final CamundaClient c = newClient(BackpressureProfile.BALANCED);
    final long start = System.nanoTime();

    // when / then
    assertThatThrownBy(
            () ->
                c.newPublishMessageCommand()
                    .messageName("order")
                    .correlationKey("1")
                    .requestTimeout(Duration.ofMillis(150))
                    .send()
                    .join())
        .isInstanceOf(ClientStatusException.class);

    // then: the retries did not take the call beyond its deadline, much less than 3 full attempts
    // with the cap of the backoff
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    assertThat(publishCalls.get()).isBetween(1, 3);
  }

  @Test
  void shouldSendTheHeadersOfEveryAttemptOnlyOnce() {
    // given
    publishOutcome = n -> n == 1 ? Status.RESOURCE_EXHAUSTED : null;
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.backpressureProfile(BackpressureProfile.BALANCED);
    builder.physicalTenantId("tenant1");
    final CamundaClient c = newClient(builder);

    // when
    publish(c).join();

    // then
    assertThat(publishCalls).hasValue(2);
    assertThat(tenantHeaders).hasSize(2).allSatisfy(h -> assertThat(h).containsExactly("tenant1"));
  }

  @Test
  void shouldNotHandleBackpressureByDefault() {
    // given: a client that was not told to handle backpressure
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED;
    final CamundaClientImpl c = newClient(new CamundaClientBuilderImpl());

    // when / then: nothing is retried, and no interceptor is in the way
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(1);
    assertThat(c.backpressure().isEnabled()).isFalse();
  }

  @Test
  void shouldNotRetryWhenDisabled() {
    // given
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED;
    final CamundaClient c = newClient(BackpressureProfile.DISABLED);

    // when / then
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(1);
  }

  @Test
  void shouldRetryButNotLimitWithTheLegacyProfile() {
    // given
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED;
    final CamundaClientImpl c = newClient(BackpressureProfile.LEGACY);

    // when / then
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(3);
    assertThat(c.backpressure().manager().state().permitsMax()).isNull();
  }

  @Test
  void shouldOnlyLimitWhenTheChannelRetriesOnItsOwn() {
    // given
    publishOutcome = n -> Status.RESOURCE_EXHAUSTED;
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.backpressureProfile(BackpressureProfile.BALANCED);
    builder.useDefaultRetryPolicy(true);
    final CamundaClientImpl c = newClient(builder);

    // when / then: no second attempt on top of the retries of the channel, but the signal counts
    assertThatThrownBy(() -> publish(c).join()).isInstanceOf(ClientStatusException.class);
    assertThat(publishCalls).hasValue(1);
    assertThat(c.backpressure().manager().state().consecutive()).isEqualTo(1);
  }

  @Test
  void shouldNeverHoldBackTheCompletionOfAJobOrTheTopologyForTheLimit() {
    // given: the limit is reached and used up
    final CamundaClientImpl c = newClient(BackpressureProfile.BALANCED);
    final BackpressureManager limiter = c.backpressure().manager();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    final Permit held = limiter.acquire().join();
    assertThat(limiter.state().permitsMax()).isEqualTo(1);

    // a call that starts new work has to wait
    final CamundaFuture<?> waiting = publish(c);
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 1);
    assertThat(publishCalls).hasValue(0);

    // when / then: calls that finish work or probe the cluster go through
    c.newCompleteCommand(7).useGrpc().send().join(5, TimeUnit.SECONDS);
    c.newTopologyRequest().useGrpc().send().join(5, TimeUnit.SECONDS);
    assertThat(completeCalls).hasValue(1);
    assertThat(topologyCalls).hasValue(1);

    held.release();
    waiting.join(5, TimeUnit.SECONDS);
    assertThat(publishCalls).hasValue(1);
  }

  @Test
  void shouldRetryTheCompletionOfAJob() {
    // given: the gateway rejects the first completion
    completeRejections.set(1);
    final CamundaClient c = newClient(BackpressureProfile.BALANCED);

    // when
    c.newCompleteCommand(7).useGrpc().send().join(5, TimeUnit.SECONDS);

    // then
    assertThat(completeCalls).hasValue(2);
  }

  @Test
  void shouldNotTouchJobActivation() {
    // given: job workers back off between polls on their own
    final CamundaClientImpl c = newClient(BackpressureProfile.BALANCED);

    // when / then
    assertThatThrownBy(
            () -> c.newActivateJobsCommand().jobType("work").maxJobsToActivate(1).send().join())
        .isInstanceOf(ClientStatusException.class);
    assertThat(activateCalls).hasValue(1);
    assertThat(c.backpressure().manager().state().consecutive()).isZero();
  }

  @Test
  void shouldWithdrawACancelledCallFromTheQueue() {
    // given
    final CamundaClientImpl c = newClient(BackpressureProfile.BALANCED);
    final BackpressureManager limiter = c.backpressure().manager();
    for (int i = 0; i < 6; i++) {
      limiter.recordBackpressure();
    }
    final Permit held = limiter.acquire().join();
    final CamundaFuture<?> waiting = publish(c);
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 1);

    // when
    waiting.cancel(true);
    held.release();

    // then
    await().atMost(Duration.ofSeconds(5)).until(() -> limiter.state().waiters() == 0);
    assertThat(limiter.state().permitsCurrent()).isZero();
    assertThat(publishCalls).hasValue(0);
  }

  private final class Gateway extends GatewayImplBase {

    @Override
    public void publishMessage(
        final PublishMessageRequest request,
        final StreamObserver<PublishMessageResponse> responseObserver) {
      final Status failure = publishOutcome.apply(publishCalls.incrementAndGet());
      if (failure != null) {
        responseObserver.onError(failure.asRuntimeException());
        return;
      }
      responseObserver.onNext(PublishMessageResponse.newBuilder().setKey(42).build());
      responseObserver.onCompleted();
    }

    @Override
    public void completeJob(
        final CompleteJobRequest request, final StreamObserver<CompleteJobResponse> observer) {
      completeCalls.incrementAndGet();
      if (completeRejections.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
        observer.onError(Status.RESOURCE_EXHAUSTED.asRuntimeException());
        return;
      }
      observer.onNext(CompleteJobResponse.getDefaultInstance());
      observer.onCompleted();
    }

    @Override
    public void topology(
        final TopologyRequest request, final StreamObserver<TopologyResponse> observer) {
      topologyCalls.incrementAndGet();
      observer.onNext(TopologyResponse.getDefaultInstance());
      observer.onCompleted();
    }

    @Override
    public void activateJobs(
        final ActivateJobsRequest request, final StreamObserver<ActivateJobsResponse> observer) {
      activateCalls.incrementAndGet();
      observer.onError(Status.RESOURCE_EXHAUSTED.asRuntimeException());
    }
  }
}
