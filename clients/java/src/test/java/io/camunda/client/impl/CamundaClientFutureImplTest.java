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
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class CamundaClientFutureImplTest {

  private final Service service = new Service();
  private final String serverName = InProcessServerBuilder.generateName();

  // using directExecutor allows us to test everything from the main thread, without concurrency
  private final Server server =
      InProcessServerBuilder.forName(serverName).addService(service).directExecutor().build();

  @BeforeEach
  void beforeEach() throws IOException {
    server.start();
  }

  @AfterEach
  void afterEach() {
    server.shutdownNow();
  }

  @Test
  void shouldCancelStreamServerSide() {
    // given
    final ActivateJobsRequest request = ActivateJobsRequest.newBuilder().setType("type").build();
    final CamundaClientFutureImpl<?, ActivateJobsResponse> future = new CamundaClientFutureImpl<>();
    try (final Client client = createClient()) {
      // when
      client.stub.activateJobs(request, future);
      future.cancel(true);

      // then
      assertThat(service.canceledCalls()).containsExactly("type");
    }
  }

  @Test
  void shouldCancelCallIfFutureAlreadyCanceled() {
    // given
    final ActivateJobsRequest request = ActivateJobsRequest.newBuilder().setType("type").build();
    final CamundaClientFutureImpl<?, ActivateJobsResponse> future = new CamundaClientFutureImpl<>();
    future.cancel(false);

    try (final Client client = createClient()) {
      // when - then
      assertThatCode(() -> client.stub.activateJobs(request, future))
          .isInstanceOf(IllegalStateException.class);
      assertThat(service.registeredCalls()).isEmpty();
    }
  }

  private Client createClient() {
    return new Client(createChannel());
  }

  private ManagedChannel createChannel() {
    return InProcessChannelBuilder.forName(serverName).directExecutor().build();
  }

  private static final class Client implements AutoCloseable {
    private final ManagedChannel channel;
    private final GatewayStub stub;

    private Client(final ManagedChannel channel) {
      this.channel = channel;
      stub = GatewayGrpc.newStub(channel);
    }

    @Override
    public void close() {
      channel.shutdownNow();
    }
  }

  private static final class Service extends GatewayImplBase {
    private final Map<String, ServerCallStreamObserver<ActivateJobsResponse>> registeredCalls =
        new HashMap<>();
    private final List<String> canceledCalls = new ArrayList<>();

    @Override
    public void activateJobs(
        final ActivateJobsRequest request,
        final StreamObserver<ActivateJobsResponse> responseObserver) {
      final String callId = request.getType();
      final ServerCallStreamObserver<ActivateJobsResponse> serverCall =
          (ServerCallStreamObserver<ActivateJobsResponse>) responseObserver;
      registeredCalls.put(callId, serverCall);
      serverCall.setOnCancelHandler(() -> canceledCalls.add(callId));
    }

    private Map<String, ServerCallStreamObserver<ActivateJobsResponse>> registeredCalls() {
      return registeredCalls;
    }

    private List<String> canceledCalls() {
      // return a new copy to avoid race conditions where the list is updated before the assertion
      // message is created, but after it was evaluated, leading to weird error messages
      return new ArrayList<>(canceledCalls);
    }
  }

  @Nested
  final class CamundaStreamingClientFutureImplTest {
    @Test
    void shouldRethrowExceptionOnCollectorError() {
      // given
      final RuntimeException error = new RuntimeException("failed");
      final CamundaStreamingClientFutureImpl<?, ActivateJobsResponse> future =
          new CamundaStreamingClientFutureImpl<>(
              null,
              ignored -> {
                throw error;
              });

      // when
      assertThatCode(() -> future.onNext(null)).isSameAs(error);
    }

    @Test
    void shouldCloseStreamOnConsumerError() {
      // given
      final ActivateJobsRequest request = ActivateJobsRequest.newBuilder().setType("type").build();
      final RuntimeException error = new RuntimeException("failed");
      final CamundaStreamingClientFutureImpl<?, ActivateJobsResponse> future =
          new CamundaStreamingClientFutureImpl<>(
              null,
              ignored -> {
                throw error;
              });
      try (final Client client = createClient()) {
        // when
        client.stub.activateJobs(request, future);
        service.registeredCalls().get("type").onNext(ActivateJobsResponse.newBuilder().build());

        // then
        assertThat(service.canceledCalls()).containsOnly("type");
      }
    }
  }
}
