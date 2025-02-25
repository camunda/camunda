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

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NettyMessagingServiceCompressionTest {

  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  @ParameterizedTest
  @EnumSource(CompressionAlgorithm.class)
  void shouldSendAndReceiveMessagesWhenCompressionEnabled(final CompressionAlgorithm algorithm) {
    // given
    var nextAddress = SocketUtil.getNextAddress();
    final var senderAddress = Address.from(nextAddress.getHostName(), nextAddress.getPort());
    final var config =
        new MessagingConfig()
            .setShutdownQuietPeriod(Duration.ofMillis(50))
            .setCompressionAlgorithm(algorithm);

    final var senderNetty =
        (ManagedMessagingService)
            new NettyMessagingService("test", senderAddress, config, registry).start().join();

    nextAddress = SocketUtil.getNextAddress();
    final var receiverAddress = Address.from(nextAddress.getHostName(), nextAddress.getPort());
    final var receiverNetty =
        (ManagedMessagingService)
            new NettyMessagingService("test", receiverAddress, config, registry).start().join();

    final String subject = "subject";
    final String requestString = "message";
    final String responseString = "success";
    receiverNetty.registerHandler(
        subject,
        (m, payload) -> {
          final String message = new String(payload);
          assertThat(message).isEqualTo(requestString);
          return CompletableFuture.completedFuture(responseString.getBytes());
        });

    // when
    final CompletableFuture<byte[]> response =
        senderNetty.sendAndReceive(receiverAddress, subject, requestString.getBytes());

    // then
    final var result = response.join();
    assertThat(new String(result)).isEqualTo(responseString);

    // teardown
    senderNetty.stop();
    receiverNetty.stop();
  }
}
