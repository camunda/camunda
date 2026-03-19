/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import io.camunda.gatekeeper.spring.session.WebSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;

class WebSessionTest {

  @Test
  void shouldTrackChangesOnSetAttribute() {
    final var session = new WebSession("test-id");
    session.clearChangeFlag();
    assertThat(session.isChanged()).isFalse();

    session.setAttribute("key", "value");

    assertThat(session.isChanged()).isTrue();
    assertThat(session.<String>getAttribute("key")).isEqualTo("value");
  }

  @Test
  void shouldTrackChangesOnRemoveAttribute() {
    final var session = new WebSession("test-id");
    session.setAttribute("key", "value");
    session.clearChangeFlag();

    session.removeAttribute("key");

    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldTrackChangesOnSetCreationTime() {
    final var session = new WebSession("test-id");
    session.clearChangeFlag();

    session.setCreationTime(Instant.now());

    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldTrackChangesOnChangeSessionId() {
    final var session = new WebSession("test-id");
    session.clearChangeFlag();

    final String newId = session.changeSessionId();

    assertThat(newId).isNotNull();
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldNotUpdateLastAccessedTimeWhenPolling() {
    final var session = new WebSession("test-id");
    final Instant original = session.getLastAccessedTime();
    session.setPolling(true);
    session.clearChangeFlag();

    session.setLastAccessedTime(Instant.now().plusSeconds(100));

    assertThat(session.getLastAccessedTime()).isEqualTo(original);
    assertThat(session.isChanged()).isFalse();
  }

  @Test
  void shouldUpdateLastAccessedTimeWhenNotPolling() {
    final var session = new WebSession("test-id");
    session.clearChangeFlag();
    final Instant newTime = Instant.now().plusSeconds(100);

    session.setLastAccessedTime(newTime);

    assertThat(session.getLastAccessedTime()).isEqualTo(newTime);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldReportContainsAuthentication() {
    final var session = new WebSession("test-id");
    assertThat(session.containsAuthentication()).isFalse();

    final var auth = new UsernamePasswordAuthenticationToken("user", "pass", List.of());
    final var secCtx = new SecurityContextImpl(auth);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, secCtx);

    assertThat(session.containsAuthentication()).isTrue();
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void shouldReportShouldBeDeletedWhenExpired() {
    final var session = new WebSession("test-id");
    session.setMaxInactiveInterval(Duration.ofSeconds(1));
    session.setLastAccessedTime(Instant.now().minusSeconds(10));

    assertThat(session.shouldBeDeleted()).isTrue();
  }

  @Test
  void shouldReportShouldBeDeletedWhenUnauthenticated() {
    final var session = new WebSession("test-id");
    final var auth = new UsernamePasswordAuthenticationToken("user", "pass");
    auth.setAuthenticated(false);
    final var secCtx = new SecurityContextImpl(auth);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, secCtx);

    assertThat(session.shouldBeDeleted()).isTrue();
  }

  @Test
  void shouldReturnSessionIdAndToString() {
    final var session = new WebSession("abc123");
    assertThat(session.getId()).isEqualTo("abc123");
    assertThat(session.toString()).contains("abc123");
  }

  @Test
  void shouldTrackChangesOnSetMaxInactiveInterval() {
    final var session = new WebSession("test-id");
    session.clearChangeFlag();

    session.setMaxInactiveInterval(Duration.ofMinutes(30));

    assertThat(session.isChanged()).isTrue();
    assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
  }
}
