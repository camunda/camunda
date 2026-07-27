/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class NodeIdProviderConfigurationUtilsTest {

  private HttpServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldReturnTaskIdFromMetadataEndpoint() {
    // given
    server.createContext(
        "/task",
        exchange -> {
          final var body =
              """
              {"TaskARN": "arn:aws:ecs:eu-central-1:123456789012:task/my-cluster/abcdef1234567890"}"""
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = NodeIdProviderConfigurationUtils.getCurrentECSTaskId(metadataUri);

    // then
    assertThat(taskId).contains("abcdef1234567890");
  }

  @Test
  void shouldReturnEmptyWhenMetadataEndpointReturnsError() {
    // given
    server.createContext(
        "/task",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = NodeIdProviderConfigurationUtils.getCurrentECSTaskId(metadataUri);

    // then
    assertThat(taskId).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenMetadataEndpointReturnsUnexpectedBody() {
    // given
    server.createContext(
        "/task",
        exchange -> {
          final var body = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = NodeIdProviderConfigurationUtils.getCurrentECSTaskId(metadataUri);

    // then
    assertThat(taskId).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenMetadataEndpointIsUnreachable() {
    // given
    server.stop(0);
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = NodeIdProviderConfigurationUtils.getCurrentECSTaskId(metadataUri);

    // then
    assertThat(taskId).isEmpty();
  }
}
