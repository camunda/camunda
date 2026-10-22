/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A client of {@code GET /v1/notifications}, which reads the stream's lines on a thread of its own,
 * so that a test can wait for a frame with a timeout.
 */
final class SseStream implements AutoCloseable {

  static final Duration TIMEOUT = Duration.ofSeconds(10);

  private static final String END = "\u0000end of stream";

  private final InputStream body;
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

  private SseStream(final InputStream body) {
    this.body = body;
    final var reader = new Thread(this::readLines, "sse-test-client");
    reader.setDaemon(true);
    reader.start();
  }

  /** Opens the stream, which is open once the headers are there. */
  static SseStream open(final HttpClient client, final URI uri) throws Exception {
    final var response =
        client.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("text/event-stream");
    return new SseStream(response.body());
  }

  /**
   * The lines of the next frame, up to its empty line: {@code event: upload} and {@code data: …}
   * for a notice, or {@code : keep-alive}. Null if none comes in time, or the stream ended.
   */
  @Nullable List<String> nextFrame(final Duration timeout) throws InterruptedException {
    final var frame = new ArrayList<String>();
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      final var line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (line == null || END.equals(line)) {
        return null;
      }
      if (line.isEmpty()) {
        return frame;
      }
      frame.add(line);
    }
  }

  /** The next notice, after any keep-alive. Fails if none comes in time. */
  UploadNotice nextNotice() throws Exception {
    final long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      final var frame = nextFrame(Duration.ofNanos(deadline - System.nanoTime()));
      if (frame == null) {
        break;
      }
      if (!frame.getFirst().startsWith(":")) {
        assertThat(frame).hasSize(2);
        assertThat(frame.getFirst()).isEqualTo("event: upload");
        assertThat(frame.get(1)).startsWith("data: ");
        return UploadNotice.fromJson(frame.get(1).substring("data: ".length()));
      }
    }
    throw new AssertionError("Expected a notice within " + TIMEOUT + ", but got none");
  }

  /** Whether the server ended the stream within the timeout. */
  boolean awaitEnd(final Duration timeout) throws InterruptedException {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      final var line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (line == null) {
        return false;
      }
      if (END.equals(line)) {
        return true;
      }
    }
  }

  @Override
  public void close() throws IOException {
    body.close();
  }

  private void readLines() {
    try (final var reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    } catch (final IOException e) {
      // The stream ended or was closed.
    }
    lines.add(END);
  }
}
