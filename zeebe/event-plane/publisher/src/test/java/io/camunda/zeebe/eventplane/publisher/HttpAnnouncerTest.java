/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HttpAnnouncerTest {

  private static final LogId LOG = new LogId("prod", "default", 3);
  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final String KEY =
      ObjectNames.segment(LOG, HISTORY, 1096001, 1096704, 0, "01JCZ4A1B2C3D4E5F6G7H8J9KM");
  private static final Upload UPLOAD =
      new Upload(
          KEY,
          184320,
          new Footer(
              HISTORY,
              1096001,
              1096704,
              1048577,
              1052032,
              Instant.parse("2026-10-08T14:00:05.010Z").toEpochMilli(),
              184105,
              io.camunda.zeebe.eventplane.segment.Level.PUBLISHER));
  private static final UploadNotice NOTICE =
      new UploadNotice(
          "prod", "default", 3, HISTORY, KEY, 1096001, 1096704, 1048577, 1052032, 184320);

  private HttpServer server;
  private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
  private volatile int status = 202;
  private final CountDownLatch release = new CountDownLatch(1);
  private volatile boolean stall;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.add(
              new Request(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("Content-Type"),
                  exchange.getRequestBody().readAllBytes()));
          if (stall) {
            try {
              release.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
  }

  @Test
  void shouldPostTheNoticeToTheUploadsEndpoint() throws Exception {
    // when
    try (final var announcer = new HttpAnnouncer(serviceUrl(""))) {
      announcer.announce(LOG, UPLOAD);
    }

    // then
    final var request = requests.poll(5, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/v1/uploads");
    assertThat(request.contentType()).isEqualTo("application/json");
    assertThat(request.body()).isEqualTo(NOTICE.toJson());
    assertThat(UploadNotice.fromJson(request.body())).isEqualTo(NOTICE);
  }

  @Test
  void shouldPostToTheUploadsEndpointBelowTheServiceUrlWithoutItsTrailingSlashes()
      throws Exception {
    for (final var suffix : new String[] {"/", "///", "/service", "/service/"}) {
      // when
      try (final var announcer = new HttpAnnouncer(serviceUrl(suffix))) {
        announcer.announce(LOG, UPLOAD);
      }

      // then
      final var request = requests.poll(5, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.path())
          .as("path of a service at '%s'", suffix)
          .isEqualTo(suffix.startsWith("/service") ? "/service/v1/uploads" : "/v1/uploads");
    }
  }

  @Test
  void shouldAnnounceASegmentWithoutRecordsWithoutPositions() throws Exception {
    // given
    final var empty =
        new Upload(
            KEY,
            100,
            new Footer(
                HISTORY,
                5,
                5,
                -1,
                -1,
                -1,
                50,
                io.camunda.zeebe.eventplane.segment.Level.PUBLISHER));

    // when
    try (final var announcer = new HttpAnnouncer(serviceUrl(""))) {
      announcer.announce(LOG, empty);
    }

    // then
    final var request = requests.poll(5, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(UploadNotice.fromJson(request.body()))
        .isEqualTo(new UploadNotice("prod", "default", 3, HISTORY, KEY, 5, 5, -1, -1, 100));
  }

  @Test
  void shouldNotThrowAndShouldLogWhenTheServiceRefuses() throws Exception {
    // given
    status = 500;

    try (final var logs = LogCapturer.capturing(HttpAnnouncer.class, Level.WARN)) {
      // when
      try (final var announcer = new HttpAnnouncer(serviceUrl(""))) {
        announcer.announce(LOG, UPLOAD);
      }

      // then
      assertThat(requests.poll(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(logs.messagesAt(Level.WARN))
          .containsExactly(
              "Expected status 202 from %s/v1/uploads for the announcement of segment %s, but got 500"
                  .formatted(serviceUrl(""), KEY));
    }
  }

  @Test
  void shouldNotThrowAndShouldLogWhenTheServiceIsGone() throws Exception {
    // given
    final int port;
    try (final var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    final var gone = URI.create("http://127.0.0.1:" + port);

    try (final var logs = LogCapturer.capturing(HttpAnnouncer.class, Level.WARN)) {
      // when
      try (final var announcer = new HttpAnnouncer(gone)) {
        announcer.announce(LOG, UPLOAD);
      }

      // then
      assertThat(logs.messagesAt(Level.WARN))
          .containsExactly(
              "Expected %s/v1/uploads to take the announcement of segment %s, but sending it failed"
                  .formatted(gone, KEY));
    }
  }

  @Test
  void shouldNotBlockAndShouldWaitOnlyBrieflyWhenClosed() {
    // given a service that doesn't answer for 10 seconds
    stall = true;
    final var announcer =
        new HttpAnnouncer(serviceUrl(""), Duration.ofMillis(300), Duration.ofMillis(300), 64);

    // when
    final long start = System.nanoTime();
    announcer.announce(LOG, UPLOAD);
    final long announced = System.nanoTime();
    announcer.close();
    final long closed = System.nanoTime();

    // then
    assertThat(Duration.ofNanos(announced - start)).isLessThan(Duration.ofSeconds(2));
    assertThat(Duration.ofNanos(closed - announced)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void shouldDropAnAnnouncementWhenTooManyAreInFlight() throws Exception {
    // given a service that doesn't answer, and one announcement in flight
    stall = true;
    final var other = new Upload("other/" + KEY, UPLOAD.size(), UPLOAD.footer());
    try (final var announcer =
        new HttpAnnouncer(serviceUrl(""), Duration.ofSeconds(5), Duration.ofSeconds(5), 1)) {
      announcer.announce(LOG, UPLOAD);
      assertThat(requests.poll(5, TimeUnit.SECONDS)).isNotNull();

      try (final var logs = LogCapturer.capturing(HttpAnnouncer.class, Level.WARN)) {
        // when
        announcer.announce(LOG, other);

        // then
        assertThat(announcer.inFlight()).isEqualTo(1);
        assertThat(logs.messagesAt(Level.WARN))
            .containsExactly(
                ("Expected at most 1 announcements in flight to %s/v1/uploads, but got more:"
                        + " dropping the one of segment %s")
                    .formatted(serviceUrl(""), other.key()));
      }
      release.countDown();
    }
    assertThat(requests).isEmpty();
  }

  @Test
  void shouldAnnounceAgainOnceTheAnnouncementsInFlightAreDone() throws Exception {
    try (final var announcer =
        new HttpAnnouncer(serviceUrl(""), Duration.ofSeconds(5), Duration.ofSeconds(5), 1)) {
      for (int i = 0; i < 3; i++) {
        // when
        announcer.announce(LOG, UPLOAD);

        // then the request arrives, so the permit it took was given back
        assertThat(requests.poll(5, TimeUnit.SECONDS)).as("request %d", i).isNotNull();
        awaitNothingInFlight(announcer);
      }
    }
  }

  @Test
  void shouldNotAnnounceAfterBeingClosed() {
    // given
    final var announcer = new HttpAnnouncer(serviceUrl(""));
    announcer.close();

    try (final var logs = LogCapturer.capturing(HttpAnnouncer.class, Level.WARN)) {
      // when
      announcer.announce(LOG, UPLOAD);

      // then
      assertThat(logs.messagesAt(Level.WARN))
          .containsExactly(
              "Expected an open announcer to announce segment %s, but it is closed".formatted(KEY));
    }
    assertThat(requests).isEmpty();
  }

  private URI serviceUrl(final String suffix) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + suffix);
  }

  private static void awaitNothingInFlight(final HttpAnnouncer announcer)
      throws InterruptedException {
    final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (announcer.inFlight() > 0 && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(announcer.inFlight()).isZero();
  }

  record Request(String method, String path, String contentType, byte[] body) {}
}
