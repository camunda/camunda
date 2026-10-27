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

import io.camunda.client.util.RecordingGatewayService;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that, when multiple {@link CamundaClient}s are each configured with their own {@code
 * physicalTenantId} and gRPC is used (not REST), every client attaches its own {@code
 * Camunda-Physical-Tenant} gRPC metadata header (via {@code PhysicalTenantInterceptor}) to its
 * outgoing calls against a shared gateway.
 *
 * <p>Complements the REST-only multi-client coverage in {@code camunda-spring-boot-starter}
 * (per-client REST path prefixing, {@code @JobWorker} fan-out) by exercising the real gRPC routing
 * path left untested there.
 */
final class MultiClientPhysicalTenantGrpcHeaderTest {

  private final RecordingGatewayService gatewayService = new RecordingGatewayService();
  private final Set<String> capturedPhysicalTenantHeaders = new CopyOnWriteArraySet<>();
  private final ServerInterceptor capturingInterceptor =
      new ServerInterceptor() {
        @Override
        public <ReqT, RespT> Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {
          final String physicalTenant = headers.get(GrpcHeaders.PHYSICAL_TENANT);
          if (physicalTenant != null) {
            capturedPhysicalTenantHeaders.add(physicalTenant);
          }
          return next.startCall(call, headers);
        }
      };
  private final Server grpcServer =
      NettyServerBuilder.forPort(0)
          .addService(ServerInterceptors.intercept(gatewayService, capturingInterceptor))
          .build();

  private CamundaClient financeClient;
  private CamundaClient riskClient;

  @BeforeEach
  void beforeEach() throws IOException {
    grpcServer.start();
  }

  @AfterEach
  void afterEach() {
    if (financeClient != null) {
      financeClient.close();
    }
    if (riskClient != null) {
      riskClient.close();
    }
    grpcServer.shutdownNow();
  }

  @Test
  void shouldSendDistinctPhysicalTenantHeaderPerClientOverGrpc() throws URISyntaxException {
    // given two clients, each scoped to its own physical tenant, both talking gRPC to the same
    // gateway (prefer-rest-over-grpc=false is the default)
    financeClient = clientBuilder("finance").build();
    riskClient = clientBuilder("risk").build();

    // when each client sends a request over gRPC
    sendTopology(financeClient);
    sendTopology(riskClient);

    // then each call carried its own client's physical-tenant header
    assertThat(capturedPhysicalTenantHeaders).containsExactlyInAnyOrder("finance", "risk");
  }

  private static void sendTopology(final CamundaClient client) {
    try {
      client.newTopologyRequest().send().join();
    } catch (final Exception ignored) {
      // only the outgoing request headers are under test; the response is irrelevant
    }
  }

  private CamundaClientBuilder clientBuilder(final String physicalTenantId)
      throws URISyntaxException {
    return CamundaClient.newClientBuilder()
        .grpcAddress(new URI("http://localhost:" + grpcServer.getPort()))
        .preferRestOverGrpc(false)
        .physicalTenantId(physicalTenantId);
  }
}
