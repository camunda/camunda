/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static io.camunda.zeebe.eventplane.service.Fixtures.HISTORY;
import static io.camunda.zeebe.eventplane.service.Fixtures.notice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.eventplane.store.FileSystemStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NotificationStreamTest {

  private static final Duration SHORT = Duration.ofMillis(300);

  @TempDir private Path directory;
  private ServiceFixture fixture;

  @BeforeEach
  void startService() throws Exception {
    fixture =
        ServiceFixture.start(
            new FileSystemStore(directory), config -> config.withKeepAlive(Duration.ofMillis(100)));
  }

  @AfterEach
  void stopService() throws Exception {
    fixture.close();
  }

  @Test
  void shouldDeliverAPostedNoticeAsAnUploadEventToASubscriberOfItsLog() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var notice = notice("c", "default", 3, HISTORY, 1, 40);

    // when
    final var response =
        fixture.post("/v1/uploads", new String(notice.toJson(), StandardCharsets.UTF_8));

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(stream.nextNotice()).isEqualTo(notice);
  }

  @Test
  void shouldPutTheNoticeOnOneDataLineOfAnUploadEvent() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var notice = notice("c", "default", 3, HISTORY, 1, 40);

    // when
    fixture.post(notice);
    var frame = stream.nextFrame(SseStream.TIMEOUT);
    while (frame != null && frame.getFirst().startsWith(":")) {
      frame = stream.nextFrame(SseStream.TIMEOUT); // a keep-alive came first
    }

    // then
    assertThat(frame)
        .containsExactly(
            "event: upload", "data: " + new String(notice.toJson(), StandardCharsets.UTF_8));
  }

  @Test
  void shouldNotDeliverANoticeToASubscriberOfAnotherLog() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var otherStream = fixture.stream("c/default/4");
    final var notice = notice("c", "default", 3, HISTORY, 1, 40);
    final var otherNotice = notice("c", "default", 4, HISTORY, 1, 7);

    // when the notice of the first log is posted before that of the other
    fixture.post(notice);
    fixture.post(otherNotice);

    // then each stream has its own log's notice first, and the other one's notice never
    assertThat(stream.nextNotice()).isEqualTo(notice);
    assertThat(otherStream.nextNotice()).isEqualTo(otherNotice);
    assertThat(onlyNotices(stream)).isEmpty();
    assertThat(onlyNotices(otherStream)).isEmpty();
  }

  @Test
  void shouldTellApartLogsOfTheSameClusterAndPartitionInAnotherTenant() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var notice = notice("c", "other", 3, HISTORY, 1, 40);
    final var expected = notice("c", "default", 3, HISTORY, 41, 50);

    // when
    fixture.post(notice);
    fixture.post(expected);

    // then
    assertThat(stream.nextNotice()).isEqualTo(expected);
  }

  @Test
  void shouldDeliverTheNoticesOfEveryLogOfAStream() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3", "c/default/4");
    final var first = notice("c", "default", 3, HISTORY, 1, 40);
    final var second = notice("c", "default", 4, HISTORY, 1, 7);

    // when
    fixture.post(first);
    fixture.post(second);

    // then
    assertThat(stream.nextNotice()).isEqualTo(first);
    assertThat(stream.nextNotice()).isEqualTo(second);
  }

  @Test
  void shouldDeliverANoticeToEveryStreamOfItsLog() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var sameLog = fixture.stream("c/default/3", "c/default/9");
    final var notice = notice("c", "default", 3, HISTORY, 1, 40);

    // when
    fixture.post(notice);

    // then
    assertThat(stream.nextNotice()).isEqualTo(notice);
    assertThat(sameLog.nextNotice()).isEqualTo(notice);
  }

  @Test
  void shouldDeliverNoticesInTheOrderOfTheirPosts() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");

    // when
    for (int i = 0; i < 20; i++) {
      fixture.post(notice("c", "default", 3, HISTORY, i * 10 + 1, i * 10 + 10));
    }

    // then
    for (int i = 0; i < 20; i++) {
      assertThat(stream.nextNotice().lastIndex()).isEqualTo(i * 10 + 10);
    }
  }

  @Test
  void shouldSendKeepAliveCommentsOnAnIdleStream() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");

    // when, then
    assertThat(stream.nextFrame(SseStream.TIMEOUT)).containsExactly(": keep-alive");
    assertThat(stream.nextFrame(SseStream.TIMEOUT)).containsExactly(": keep-alive");
  }

  @Test
  void shouldRemoveASubscriberWhoseClientWentAway() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    final var staying = fixture.stream("c/default/3");
    await().until(() -> fixture.service().openStreams() == 2);

    // when
    stream.close();

    // then the idle keep-alives find out
    await().atMost(SseStream.TIMEOUT).until(() -> fixture.service().openStreams() == 1);
    final var notice = notice("c", "default", 3, HISTORY, 1, 40);
    fixture.post(notice);
    assertThat(staying.nextNotice()).isEqualTo(notice);
  }

  @Test
  void shouldEndTheStreamsWhenTheServiceCloses() throws Exception {
    // given
    final var stream = fixture.stream("c/default/3");
    await().until(() -> fixture.service().openStreams() == 1);

    // when
    fixture.service().close();

    // then
    assertThat(stream.awaitEnd(SseStream.TIMEOUT)).isTrue();
    assertThat(fixture.service().openStreams()).isZero();
  }

  /** What the stream shows within a short time, if it isn't a keep-alive. */
  private static List<List<String>> onlyNotices(final SseStream stream)
      throws InterruptedException {
    final var frames = new ArrayList<List<String>>();
    final long deadline = System.nanoTime() + SHORT.toNanos();
    while (System.nanoTime() < deadline) {
      final var frame = stream.nextFrame(Duration.ofNanos(deadline - System.nanoTime()));
      if (frame != null && !frame.getFirst().startsWith(":")) {
        frames.add(frame);
      }
    }
    return frames;
  }
}
