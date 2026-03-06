/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.holder;

import static io.camunda.auth.spring.holder.HttpSessionBasedAuthenticationHolder.CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.auth.domain.model.CamundaAuthentication;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpSessionBasedAuthenticationHolderTest {

  private MockHttpServletRequest request;
  private HttpSessionBasedAuthenticationHolder holder;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    holder = new HttpSessionBasedAuthenticationHolder(Duration.ofSeconds(1));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldSupportWhenSessionExists() {
    // given
    request.setSession(new MockHttpSession());

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotSupportWhenSessionDoesNotExist() {
    // given — no session set on request

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnAuthentication() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var session = new MockHttpSession();
    request.setSession(session);
    holder.set(authentication);

    // when
    final var result = holder.get();

    // then
    assertThat(result).isEqualTo(authentication);
  }

  @Test
  void shouldAddAuthenticationToSession() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var session = new MockHttpSession();
    request.setSession(session);

    // when
    holder.set(authentication);

    // then
    assertThat(session.getAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY))
        .isEqualTo(authentication);
  }

  @Test
  void shouldReturnNullIfAuthenticationNotRefreshed() throws InterruptedException {
    // given
    holder = new HttpSessionBasedAuthenticationHolder(Duration.ofMillis(100));
    final var authentication = mock(CamundaAuthentication.class);
    final var session = new MockHttpSession();
    request.setSession(session);
    holder.set(authentication);
    Thread.sleep(110L);

    // when
    final var result = holder.get();

    // then
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullWhenNoRequestContext() {
    // given
    RequestContextHolder.resetRequestAttributes();

    // when
    final var result = holder.get();

    // then
    assertThat(result).isNull();
  }
}
