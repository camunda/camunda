/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.scim2.common.exceptions.ScimException;
import io.camunda.gateway.scim.ScimAttributeProperties.SourceProperties;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScimSourcesTest {

  @Test
  void shouldUseImplicitDefaultSourceAcceptingAnyClientWhenNothingIsConfigured() {
    // when
    final var sources = new ScimSources(List.of());

    // then
    assertThat(sources.ids()).containsExactly("default");
    assertThat(sources.resolve("any-client"))
        .get()
        .satisfies(s -> assertThat(s.id()).isEqualTo("default"));
    assertThat(sources.resolve(null)).isPresent();
    assertThat(sources.resolve("x").get().adoptUnowned()).isTrue();
  }

  @Test
  void shouldAcceptAnyClientForSingleSourceWithoutClientIds() {
    // when
    final var sources = new ScimSources(List.of(source("entra", List.of(), null, null, false)));

    // then
    assertThat(sources.resolve("whoever")).isPresent();
    assertThat(sources.resolve("whoever").get().adoptUnowned()).isFalse();
  }

  @Test
  void shouldRestrictSingleSourceToListedClients() {
    // when
    final var sources =
        new ScimSources(List.of(source("entra", List.of("c1"), "entra", "oid", false)));

    // then
    assertThat(sources.resolve("c1")).isPresent();
    assertThat(sources.resolve("c2")).isEmpty();
    assertThat(sources.resolve(null)).isEmpty();
  }

  @Test
  void shouldResolveSourceByClientIdWithMultipleSources() {
    // given
    final var sources =
        new ScimSources(
            List.of(
                source("a", List.of("ca"), "ra", null, false),
                source("b", List.of("cb1", "cb2"), "rb", "sub", true)));

    // then
    assertThat(sources.resolve("cb2").get().id()).isEqualTo("b");
    assertThat(sources.resolve("ca").get().id()).isEqualTo("a");
    assertThat(sources.resolve("other")).isEmpty();
    assertThat(sources.get("b")).isPresent();
    assertThat(sources.get("zzz")).isEmpty();
    assertThat(sources.ids()).containsExactly("a", "b");
  }

  @Test
  void shouldRejectUnknownClientWith403() {
    // given
    final var sources = new ScimSources(List.of(source("a", List.of("ca"), "ra", null, false)));

    // then
    assertThatThrownBy(() -> sources.require(CamundaAuthentication.of(a -> a.clientId("other"))))
        .isInstanceOfSatisfying(
            ScimException.class, e -> assertThat(e.getScimError().getStatus()).isEqualTo(403));
  }

  @Test
  void shouldTellWhichEntitiesASourceSees() {
    // given
    final var sources =
        new ScimSources(
            List.of(
                source("a", List.of("ca"), "ra", null, false),
                source("b", List.of("cb"), "rb", null, true)));

    // then
    final var a = sources.get("a").get();
    final var b = sources.get("b").get();
    assertThat(a.canSee("a")).isTrue();
    assertThat(a.canSee("b")).isFalse();
    assertThat(a.canSee(null)).isFalse();
    assertThat(a.canSee("")).isFalse();
    assertThat(b.canSee("")).isTrue();
    assertThat(b.canSee("a")).isFalse();
  }

  @Test
  void shouldRequireClientIdsAndRegistrationWithMultipleSources() {
    assertThatThrownBy(
            () ->
                new ScimSources(
                    List.of(
                        source("a", List.of("c1"), "ra", null, false),
                        source("b", List.of(), "rb", null, false))))
        .hasMessageContaining("client-ids");
    assertThatThrownBy(
            () ->
                new ScimSources(
                    List.of(
                        source("a", List.of("c1"), "ra", null, false),
                        source("b", List.of("c2"), null, null, false))))
        .hasMessageContaining("oidc-registration");
  }

  @Test
  void shouldRejectOverlappingClientsAndDuplicateRegistrationsAndIds() {
    assertThatThrownBy(
            () ->
                new ScimSources(
                    List.of(
                        source("a", List.of("c1"), "ra", null, false),
                        source("b", List.of("c1"), "rb", null, false))))
        .hasMessageContaining("c1");
    assertThatThrownBy(
            () ->
                new ScimSources(
                    List.of(
                        source("a", List.of("c1"), "ra", null, false),
                        source("b", List.of("c2"), "ra", null, false))))
        .hasMessageContaining("oidc-registration 'ra'");
    assertThatThrownBy(
            () ->
                new ScimSources(
                    List.of(
                        source("a", List.of("c1"), "ra", null, false),
                        source("a", List.of("c2"), "rb", null, false))))
        .hasMessageContaining("more than once");
  }

  @Test
  void shouldRejectInvalidSourceIds() {
    assertThatThrownBy(() -> new ScimSources(List.of(source("a b", List.of(), null, null, false))))
        .hasMessageContaining("must match");
    assertThatThrownBy(() -> new ScimSources(List.of(source(null, List.of(), null, null, false))))
        .isInstanceOf(IllegalStateException.class);
  }

  private static SourceProperties source(
      final String id,
      final List<String> clientIds,
      final String registration,
      final String claim,
      final boolean adopt) {
    final var source = new SourceProperties();
    source.setId(id);
    source.setClientIds(clientIds);
    source.setOidcRegistration(registration);
    source.setLoginBindingClaim(claim);
    source.setAdoptUnowned(adopt);
    return source;
  }
}
