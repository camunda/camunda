/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

/**
 * Verifies that a configured read timeout takes effect, using an endpoint that accepts connections
 * but never answers. Without a configured timeout, the AWS SDK would wait for its default of 30s
 * per attempt, so the assertions below would not hold.
 */
final class RequestTimeoutTest {
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

  private ServerSocket serverSocket;
  private ExecutorService acceptor;
  private final List<Socket> acceptedConnections = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startStallingServer() throws IOException {
    serverSocket = new ServerSocket(0);
    acceptor = Executors.newSingleThreadExecutor();
    acceptor.submit(
        () -> {
          while (!serverSocket.isClosed()) {
            // Hold on to the connection so that it stays open without ever writing a response.
            acceptedConnections.add(serverSocket.accept());
          }
          return null;
        });
  }

  @AfterEach
  void stopStallingServer() throws IOException {
    serverSocket.close();
    acceptor.shutdownNow();
    for (final var connection : acceptedConnections) {
      connection.close();
    }
  }

  @Test
  void shouldFailRequestAfterReadTimeout() {
    // given
    final var store = storeWith(builder -> builder.withReadTimeout(READ_TIMEOUT));

    // when
    final var status = store.getStatus(new BackupIdentifierImpl(1, 1, 1));

    // then
    assertThat(status)
        .failsWithin(Duration.ofSeconds(15))
        .withThrowableOfType(ExecutionException.class)
        .withStackTraceContaining("Read timed out");
  }

  private S3BackupStore storeWith(final UnaryOperator<Builder> timeouts) {
    final var config =
        timeouts
            .apply(
                new Builder()
                    .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
                    .withEndpoint("http://localhost:%d".formatted(serverSocket.getLocalPort()))
                    .withRegion(Region.US_EAST_1.id())
                    .withCredentials("letmein", "letmein1234")
                    .forcePathStyleAccess(true))
            .build();
    return new S3BackupStore(config, S3BackupStore.buildClient(config));
  }
}
