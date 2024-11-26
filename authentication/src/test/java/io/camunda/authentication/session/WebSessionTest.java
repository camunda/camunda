/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;

public class WebSessionTest {

  @Test
  public void changeSessionId() {
    // given
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);

    // when
    session.changeSessionId();

    // then
    assertThat(session.getId()).isNotEqualTo(sessionId);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void setCreationTime() {
    // given
    final var now = Instant.now();
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);

    // when
    session.setCreationTime(now);

    // then
    assertThat(session.getCreationTime()).isEqualTo(now);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void setLastAccessedTime() {
    // given
    final var now = Instant.now();
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);

    // when
    session.setLastAccessedTime(now);

    // then
    assertThat(session.getLastAccessedTime()).isEqualTo(now);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void shouldNotUpdateLastAccessedTimeWhenPollingIsSetToTrue() {
    // given
    final var now = Instant.now();
    final var lastAccessedTime = now.plus(Duration.ofSeconds(60));
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);
    session.setLastAccessedTime(now);
    session.clearChangeFlag();
    session.setPolling(true);

    // when
    session.setLastAccessedTime(lastAccessedTime);

    // then
    assertThat(session.getLastAccessedTime()).isEqualTo(now);
    assertThat(session.isChanged()).isFalse();
  }

  @Test
  public void setMaxInactiveInterval() {
    // given
    final var duration = Duration.ofSeconds(1800);
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);

    // when
    session.setMaxInactiveInterval(duration);

    // then
    assertThat(session.getMaxInactiveInterval()).isEqualTo(duration);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void setAttribute() {
    // given
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);

    // when
    session.setAttribute("foo", "bar");

    // then
    assertThat((String) session.getAttribute("foo")).isEqualTo("bar");
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void removeAttribute() {
    // given
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);
    session.setAttribute("foo", "bar");
    session.clearChangeFlag();

    // when
    session.removeAttribute("foo");

    // then
    assertThat((Object) session.getAttribute("foo")).isNull();
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  public void isExpired() {
    // given
    final var now = Instant.now();
    final var duration = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);
    session.setMaxInactiveInterval(duration);
    session.setLastAccessedTime(
        now.minus(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 2)));

    // when + then
    assertThat(session.isExpired()).isTrue();
  }

  @Test
  public void shouldBeDeleted() {
    // given
    final var now = Instant.now();
    final var duration = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
    final var sessionId = "sessionId";
    final var session = new WebSession(sessionId);
    session.setMaxInactiveInterval(duration);
    session.setLastAccessedTime(
        now.minus(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 2)));

    // when + then
    assertThat(session.shouldBeDeleted()).isTrue();
  }
}
