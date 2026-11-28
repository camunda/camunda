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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ClientConnectionException;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientHttpException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TransportFailureTranslatorTest {

  private static final URI GATEWAY_ADDRESS = URI.create("http://gateway:8080");

  private final TransportFailureTranslator translator =
      new TransportFailureTranslator(GATEWAY_ADDRESS);

  @ParameterizedTest
  @MethodSource("connectionFailures")
  void shouldTranslateConnectionFailure(final Throwable failure) {
    // when
    final ClientException translated = translator.translate(failure);

    // then
    assertThat(translated)
        .isInstanceOf(ClientConnectionException.class)
        .hasMessageContaining(GATEWAY_ADDRESS.toString())
        .hasCause(failure);
    assertThat(((ClientConnectionException) translated).address()).isEqualTo(GATEWAY_ADDRESS);
  }

  @Test
  void shouldDetectConnectionFailureNestedInCauseChain() {
    // given
    final IOException failure = new IOException("wrapper", new ConnectException("refused"));

    // when
    final ClientException translated = translator.translate(failure);

    // then
    assertThat(translated).isInstanceOf(ClientConnectionException.class).hasCause(failure);
  }

  @Test
  void shouldPassThroughClientException() {
    // given
    final ClientException failure = new ClientHttpException(503, "Service Unavailable");

    // when
    final ClientException translated = translator.translate(failure);

    // then
    assertThat(translated).isSameAs(failure);
  }

  @Test
  void shouldWrapOtherFailuresInGenericClientException() {
    // given
    final IOException failure = new IOException("connection reset");

    // when
    final ClientException translated = translator.translate(failure);

    // then
    assertThat(translated).isExactlyInstanceOf(ClientException.class).hasCause(failure);
  }

  @Test
  void shouldNotTreatReadTimeoutAsConnectionFailure() {
    // given - the gateway may already have received the request
    final SocketTimeoutException failure = new SocketTimeoutException("Read timed out");

    // when
    final ClientException translated = translator.translate(failure);

    // then
    assertThat(translated).isExactlyInstanceOf(ClientException.class).hasCause(failure);
  }

  @Test
  void shouldTerminateOnCyclicCauseChain() {
    // given
    final IOException first = new IOException("first");
    final IOException second = new IOException("second", first);
    first.initCause(second);

    // when
    final ClientException translated = translator.translate(first);

    // then
    assertThat(translated).isExactlyInstanceOf(ClientException.class);
  }

  private static Stream<Throwable> connectionFailures() {
    return Stream.of(
        new ConnectException("Connection refused"),
        new ConnectTimeoutException("Connect timed out"),
        new NoRouteToHostException("No route to host"),
        new UnknownHostException("gateway"));
  }
}
