/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ActiveComponentsResolverTest {

  @Test
  void shouldReturnAllKnownComponentsWhenNoPropertiesAreSet() {
    // given — no properties: both legacy and unified keys default to true
    final var resolver = new ActiveComponentsResolver(new MockEnvironment());

    // when
    final var result = resolver.resolve();

    // then — all three components are enabled; list is sorted alphabetically
    assertThat(result).containsExactly("admin", "operate", "tasklist");
  }

  @Test
  void shouldExcludeComponentDisabledViaUnifiedKey() {
    // given
    final var env = new MockEnvironment().withProperty("camunda.webapps.operate.enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).containsExactly("admin", "tasklist");
  }

  @Test
  void shouldExcludeComponentDisabledViaLegacyKey() {
    // given
    final var env = new MockEnvironment().withProperty("camunda.admin.webapp-enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).containsExactly("operate", "tasklist");
  }

  @Test
  void shouldExcludeComponentWhenUiIsDisabledButBackendIsEnabled() {
    // given — backend is enabled but UI is explicitly disabled; the resolver tracks UI components,
    // so this component must not appear in activeComponents (mirrors the property set used by
    // ConditionalOnWebappUiEnabled)
    final var env =
        new MockEnvironment().withProperty("camunda.webapps.tasklist.ui-enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).containsExactly("admin", "operate");
  }

  @Test
  void shouldExcludeComponentWhenBothKeysAreExplicitlyFalse() {
    // given
    final var env =
        new MockEnvironment()
            .withProperty("camunda.tasklist.webapp-enabled", "false")
            .withProperty("camunda.webapps.tasklist.enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).containsExactly("admin", "operate");
  }

  @Test
  void shouldReturnEmptyListWhenAllComponentsAreDisabled() {
    // given
    final var env =
        new MockEnvironment()
            .withProperty("camunda.webapps.admin.enabled", "false")
            .withProperty("camunda.webapps.operate.enabled", "false")
            .withProperty("camunda.webapps.tasklist.enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldAlwaysReturnResultInAlphabeticalOrder() {
    // given — only tasklist enabled, verify the result is still sorted (single element case)
    final var env =
        new MockEnvironment()
            .withProperty("camunda.webapps.admin.enabled", "false")
            .withProperty("camunda.webapps.operate.enabled", "false");
    final var resolver = new ActiveComponentsResolver(env);

    // when
    final var result = resolver.resolve();

    // then
    assertThat(result).containsExactly("tasklist");
  }
}
