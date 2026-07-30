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

final class ECSTaskIdResolverTest {

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
    final var taskId = ECSTaskIdResolver.resolve(metadataUri);

    // then
    assertThat(taskId).contains("abcdef1234567890");
  }

  @Test
  void shouldGiveUpWhenMetadataEndpointKeepsReturningServerError() {
    // given
    server.createContext(
        "/task",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = ECSTaskIdResolver.resolve(metadataUri, 200, 10);

    // then
    assertThat(taskId).isEmpty();
  }

  @Test
  void shouldStopImmediatelyWhenMetadataEndpointReturnsNotFound() {
    // given
    final var attempts = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext(
        "/task",
        exchange -> {
          attempts.incrementAndGet();
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = ECSTaskIdResolver.resolve(metadataUri, 5_000, 10);

    // then
    assertThat(taskId).isEmpty();
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void shouldGiveUpWhenMetadataEndpointKeepsReturningIncompleteBody() {
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
    final var taskId = ECSTaskIdResolver.resolve(metadataUri, 200, 10);

    // then
    assertThat(taskId).isEmpty();
  }

  @Test
  void shouldRetryUntilMetadataEndpointReturnsTaskId() {
    // given
    final var attempts = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext(
        "/task",
        exchange -> {
          if (attempts.incrementAndGet() < 3) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
          }
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
    final var taskId = ECSTaskIdResolver.resolve(metadataUri, 5_000, 10);

    // then
    assertThat(taskId).contains("abcdef1234567890");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void shouldNotQueryMetadataWhenResolveDisabled() {
    // given a metadata endpoint that would return a valid task id
    final var attempts = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext(
        "/task",
        exchange -> {
          attempts.incrementAndGet();
          final var body =
              """
              {"TaskARN": "arn:aws:ecs:eu-central-1:123456789012:task/my-cluster/abcdef1234567890"}"""
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when resolution is disabled
    final var taskId = ECSTaskIdResolver.resolve(false, metadataUri);

    // then it returns empty without contacting the endpoint
    assertThat(taskId).isEmpty();
    assertThat(attempts.get()).isZero();
  }

  @Test
  void shouldQueryMetadataWhenResolveEnabled() {
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

    // when resolution is enabled
    final var taskId = ECSTaskIdResolver.resolve(true, metadataUri);

    // then
    assertThat(taskId).contains("abcdef1234567890");
  }

  @Test
  void shouldReturnEmptyWhenMetadataEndpointIsUnreachable() {
    // given
    server.stop(0);
    final var metadataUri = "http://localhost:" + server.getAddress().getPort();

    // when
    final var taskId = ECSTaskIdResolver.resolve(metadataUri, 200, 10);

    // then
    assertThat(taskId).isEmpty();
  }
}
