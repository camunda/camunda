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
    final var webSession = new WebSession(sessionId);

    // when
    webSession.changeSessionId();

    // then
    assertThat(webSession.getId()).isNotEqualTo(sessionId);
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void setCreationTime() {
    // given
    final var now = Instant.now();
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);

    // when
    webSession.setCreationTime(now);

    // then
    assertThat(webSession.getCreationTime()).isEqualTo(now);
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void setLastAccessedTime() {
    // given
    final var now = Instant.now();
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);

    // when
    webSession.setLastAccessedTime(now);

    // then
    assertThat(webSession.getLastAccessedTime()).isEqualTo(now);
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void shouldNotUpdateLastAccessedTimeWhenPollingIsSetToTrue() {
    // given
    final var now = Instant.now();
    final var lastAccessedTime = now.plus(Duration.ofSeconds(60));
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);
    webSession.setLastAccessedTime(now);
    webSession.clearChangeFlag();
    webSession.setPolling(true);

    // when
    webSession.setLastAccessedTime(lastAccessedTime);

    // then
    assertThat(webSession.getLastAccessedTime()).isEqualTo(now);
    assertThat(webSession.isChanged()).isFalse();
  }

  @Test
  public void setMaxInactiveInterval() {
    // given
    final var duration = Duration.ofSeconds(1800);
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);

    // when
    webSession.setMaxInactiveInterval(duration);

    // then
    assertThat(webSession.getMaxInactiveInterval()).isEqualTo(duration);
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void setAttribute() {
    // given
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);

    // when
    webSession.setAttribute("foo", "bar");

    // then
    assertThat((String) webSession.getAttribute("foo")).isEqualTo("bar");
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void removeAttribute() {
    // given
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);
    webSession.setAttribute("foo", "bar");
    webSession.clearChangeFlag();

    // when
    webSession.removeAttribute("foo");

    // then
    assertThat((Object) webSession.getAttribute("foo")).isNull();
    assertThat(webSession.isChanged()).isTrue();
  }

  @Test
  public void isExpired() {
    // given
    final var now = Instant.now();
    final var duration = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);
    webSession.setMaxInactiveInterval(duration);
    webSession.setLastAccessedTime(
        now.minus(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 2)));

    // when + then
    assertThat(webSession.isExpired()).isTrue();
  }

  @Test
  public void shouldBeDeleted() {
    // given
    final var now = Instant.now();
    final var duration = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
    final var sessionId = "sessionId";
    final var webSession = new WebSession(sessionId);
    webSession.setMaxInactiveInterval(duration);
    webSession.setLastAccessedTime(
        now.minus(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 2)));

    // when + then
    assertThat(webSession.shouldBeDeleted()).isTrue();
  }
}
