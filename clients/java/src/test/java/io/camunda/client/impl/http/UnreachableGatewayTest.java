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
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientConnectionException;
import io.camunda.client.api.command.ClientException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every REST request leaves the transport through the same failure path, so requests of different
 * HTTP methods and endpoints must all report an unreachable gateway the same way.
 */
final class UnreachableGatewayTest {

  @ParameterizedTest
  @MethodSource("requests")
  void shouldFailWithConnectionExceptionWhenGatewayIsUnreachable(
      final Function<CamundaClient, CamundaFuture<?>> request) throws IOException {
    // given
    final URI restAddress = URI.create("http://localhost:" + unusedPort());

    try (final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(restAddress)
            .preferRestOverGrpc(true)
            .build()) {

      // when / then
      assertThatThrownBy(() -> request.apply(client).join())
          .isInstanceOf(ClientConnectionException.class)
          // existing callers catch ClientException; they must keep working
          .isInstanceOf(ClientException.class)
          .hasMessageContaining(restAddress.toString())
          .hasRootCauseInstanceOf(ConnectException.class);
    }
  }

  private static Stream<Named<Function<CamundaClient, CamundaFuture<?>>>> requests() {
    return Stream.of(
        named("topology (GET)", client -> client.newTopologyRequest().useRest().send()),
        named(
            "process definition search (POST)",
            client -> client.newProcessDefinitionSearchRequest().send()),
        named(
            "create process instance (POST)",
            client -> client.newCreateInstanceCommand().processDefinitionKey(1L).send()));
  }

  private static int unusedPort() throws IOException {
    try (final ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
