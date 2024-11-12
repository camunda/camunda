/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class NettyMessagingServiceTlsTest {

  @Test
  void shouldCommunicateOverTls() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var client = createSecureMessagingService(certificate);
    final var server = createSecureMessagingService(certificate);
    final var payload = "foo".getBytes();

    // when
    client.start().join();
    server.start().join();
    server.registerHandler(
        "topic",
        (sender, request) ->
            CompletableFuture.completedFuture((new String(request) + "bar").getBytes()));
    final var response = client.sendAndReceive(server.address(), "topic", payload).join();

    // then
    assertThat(response).isEqualTo("foobar".getBytes());
  }

  @Test
  void shouldFailWhenClientIsNotUsingTls() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var client = createInsecureMessagingService();
    final var server = createSecureMessagingService(certificate);
    final var payload = "foo".getBytes();

    // when
    client.start().join();
    server.start().join();
    server.registerHandler(
        "topic",
        (sender, request) ->
            CompletableFuture.completedFuture((new String(request) + "bar").getBytes()));
    final var response =
        client.sendAndReceive(server.address(), "topic", payload, true, Duration.ofSeconds(10));

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.ConnectionClosed.class);
  }

  @Test
  void shouldFailWhenServerIsNotUsingTls() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var server = createInsecureMessagingService();
    final var client = createSecureMessagingService(certificate);
    final var payload = "foo".getBytes();

    // when
    client.start().join();
    server.start().join();
    server.registerHandler(
        "topic",
        (sender, request) ->
            CompletableFuture.completedFuture((new String(request) + "bar").getBytes()));
    final var response =
        client.sendAndReceive(server.address(), "topic", payload, true, Duration.ofSeconds(1));

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(2))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.ConnectionClosed.class);
  }

  private NettyMessagingService createInsecureMessagingService() {
    final var config =
        new MessagingConfig().setPort(SocketUtil.getNextAddress().getPort()).setTlsEnabled(false);
    return new NettyMessagingService(
        "cluster", Address.from(config.getPort()), config, "insecureTestPrefix");
  }

  private NettyMessagingService createSecureMessagingService(
      final SelfSignedCertificate certificate) {
    final var config =
        new MessagingConfig()
            .setPort(SocketUtil.getNextAddress().getPort())
            .setTlsEnabled(true)
            .setCertificateChain(certificate.certificate())
            .setPrivateKey(certificate.privateKey());
    return new NettyMessagingService(
        "cluster", Address.from(config.getPort()), config, "secureTestPrefix");
  }
}
