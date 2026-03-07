/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;

class WebSessionTest {

  @Test
  void shouldTrackChangesOnSetAttribute() {
    // given
    final var session = new WebSession("test-session");

    // when
    session.setAttribute("key", "value");

    // then
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldTrackChangesOnRemoveAttribute() {
    // given
    final var session = new WebSession("test-session");
    session.setAttribute("key", "value");
    session.clearChangeFlag();

    // when
    session.removeAttribute("key");

    // then
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldTrackChangesOnChangeSessionId() {
    // given
    final var session = new WebSession("test-session");
    session.clearChangeFlag();

    // when
    session.changeSessionId();

    // then
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldClearChangeFlag() {
    // given
    final var session = new WebSession("test-session");
    session.setAttribute("key", "value");
    assertThat(session.isChanged()).isTrue();

    // when
    session.clearChangeFlag();

    // then
    assertThat(session.isChanged()).isFalse();
  }

  @Test
  void shouldNotUpdateLastAccessedTimeWhenPolling() {
    // given
    final var session = new WebSession("test-session");
    final var originalLastAccessedTime = session.getLastAccessedTime();
    session.clearChangeFlag();
    session.setPolling(true);

    // when
    session.setLastAccessedTime(Instant.now().plusSeconds(60));

    // then
    assertThat(session.getLastAccessedTime()).isEqualTo(originalLastAccessedTime);
    assertThat(session.isChanged()).isFalse();
  }

  @Test
  void shouldUpdateLastAccessedTimeWhenNotPolling() {
    // given
    final var session = new WebSession("test-session");
    session.setPolling(false);
    session.clearChangeFlag();
    final var newLastAccessedTime = Instant.now().plusSeconds(60);

    // when
    session.setLastAccessedTime(newLastAccessedTime);

    // then
    assertThat(session.getLastAccessedTime()).isEqualTo(newLastAccessedTime);
    assertThat(session.isChanged()).isTrue();
  }

  @Test
  void shouldDetectExpiredSession() {
    // given
    final var session = new WebSession("test-session");
    session.setMaxInactiveInterval(Duration.ZERO);

    // when/then
    assertThat(session.isExpired()).isTrue();
  }

  @Test
  void shouldBeDeletedWhenExpired() {
    // given
    final var session = new WebSession("test-session");
    session.setMaxInactiveInterval(Duration.ZERO);

    // when
    final var result = session.shouldBeDeleted();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldBeDeletedWhenUnauthenticated() {
    // given
    final var session = new WebSession("test-session");
    final var auth = new TestingAuthenticationToken("user", "pass");
    auth.setAuthenticated(false);
    final var securityContext = new SecurityContextImpl(auth);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, securityContext);

    // when
    final var result = session.shouldBeDeleted();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotBeDeletedWhenAuthenticated() {
    // given
    final var session = new WebSession("test-session");
    final var auth = new TestingAuthenticationToken("user", "pass", "ROLE_USER");
    auth.setAuthenticated(true);
    final var securityContext = new SecurityContextImpl(auth);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, securityContext);

    // when
    final var result = session.shouldBeDeleted();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldNotBeDeletedWhenNoAuthentication() {
    // given
    final var session = new WebSession("test-session");

    // when
    final var result = session.shouldBeDeleted();

    // then
    assertThat(result).isFalse();
  }
}
