/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiveEventPlaneClient.Answer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HttpLogArchiveEventPlaneClientTest {

  private HttpServer server;
  private final AtomicReference<String> path = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> contentType = new AtomicReference<>();
  private final AtomicReference<byte[]> body = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String response = "{\"nextSeq\":3}";

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getRawPath());
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          body.set(exchange.getRequestBody().readAllBytes());
          final var bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldPostRawEntriesToEncodedUrlWithBasicAuthAndParseNextSeq() {
    // given
    final var client = client("user", "secret");
    final var first = "{\"seq\": 1,  \"b\":\"x\"}".getBytes(StandardCharsets.UTF_8);
    final var second = "{\"seq\":2}".getBytes(StandardCharsets.UTF_8);

    // when
    final var answer = client.send(2, "tl-0.f3", List.of(first, second)).join();

    // then
    assertThat(answer).isEqualTo(new Answer(200, 3));
    assertThat(path.get())
        .isEqualTo("/v1/log-archive/my%20cluster/default/partitions/2/timelines/tl-0.f3/commits");
    assertThat(new String(body.get(), StandardCharsets.UTF_8))
        .isEqualTo("{\"entries\":[{\"seq\": 1,  \"b\":\"x\"},{\"seq\":2}]}");
    assertThat(authorization.get()).isEqualTo("Basic dXNlcjpzZWNyZXQ=");
    assertThat(contentType.get()).isEqualTo("application/json");
  }

  @Test
  void shouldSendNoAuthorizationHeaderWithoutUsername() {
    // given
    final var client = client(null, null);

    // when
    client.send(1, "tl-0", List.of("{}".getBytes(StandardCharsets.UTF_8))).join();

    // then
    assertThat(authorization.get()).isNull();
  }

  @Test
  void shouldReturnStatusAndNextSeqOf409() {
    // given
    status = 409;
    response = "{\"nextSeq\":1}";

    // when
    final var answer =
        client(null, null).send(1, "tl-0", List.of("{}".getBytes(StandardCharsets.UTF_8))).join();

    // then
    assertThat(answer).isEqualTo(new Answer(409, 1));
  }

  @Test
  void shouldReturnNoNextSeqForProblemDetail() {
    // given
    status = 400;
    response = "{\"title\":\"bad\"}";

    // when
    final var answer =
        client(null, null).send(1, "tl-0", List.of("{}".getBytes(StandardCharsets.UTF_8))).join();

    // then
    assertThat(answer).isEqualTo(new Answer(400, -1));
  }

  private HttpLogArchiveEventPlaneClient client(final String username, final String password) {
    return new HttpLogArchiveEventPlaneClient(
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "my cluster",
        "default",
        username,
        password,
        Duration.ofSeconds(5));
  }
}
