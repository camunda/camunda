/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.holder;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spring.holder.RequestContextBasedAuthenticationHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class RequestContextBasedAuthenticationHolderTest {

  private MockHttpServletRequest request;
  private RequestContextBasedAuthenticationHolder holder;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    holder = new RequestContextBasedAuthenticationHolder(request);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldSupportWhenNoSessionExists() {
    assertThat(holder.supports()).isTrue();
  }

  @Test
  void shouldNotSupportWhenSessionExists() {
    // given — force session creation
    request.getSession(true);

    // when / then
    assertThat(holder.supports()).isFalse();
  }

  @Test
  void shouldSetAndGetAuthentication() {
    // given
    final var auth = CamundaAuthentication.of(b -> b.user("alice"));

    // when
    holder.set(auth);

    // then
    assertThat(holder.get()).isEqualTo(auth);
  }

  @Test
  void shouldReturnNullWhenNotSet() {
    assertThat(holder.get()).isNull();
  }
}
