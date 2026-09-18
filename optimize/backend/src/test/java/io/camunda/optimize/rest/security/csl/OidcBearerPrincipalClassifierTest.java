/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OidcBearerPrincipalClassifierTest {

  private OidcBearerPrincipalClassifier classifierWith(final boolean preferUsernameClaim) {
    final OidcConfiguration configuration = new OidcConfiguration();
    configuration.setUsernameClaim("preferred_username");
    configuration.setClientIdClaim("client_id");
    configuration.setPreferUsernameClaim(preferUsernameClaim);
    return new OidcBearerPrincipalClassifier(configuration);
  }

  @Test
  void shouldRequireCheckForAUsernameOnlyClaimsSet() {
    // given
    final var classifier = classifierWith(false);
    final Map<String, Object> claims = Map.of("preferred_username", "noopt");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotRequireCheckForAClientIdOnlyClaimsSet() {
    // given
    final var classifier = classifierWith(false);
    final Map<String, Object> claims = Map.of("client_id", "optimize-api-client");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldPreferUsernameOverClientIdWhenConfigured() {
    // given
    final var classifier = classifierWith(true);
    final Map<String, Object> claims =
        Map.of("preferred_username", "noopt", "client_id", "optimize-api-client");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldTreatClientIdAsClientWhenUsernameNotPreferred() {
    // given
    final var classifier = classifierWith(false);
    final Map<String, Object> claims =
        Map.of("preferred_username", "noopt", "client_id", "optimize-api-client");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldFailClosedWhenNeitherClaimIsPresent() {
    // given
    final var classifier = classifierWith(false);
    final Map<String, Object> claims = Map.of("sub", "some-subject-with-no-mapped-claims");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldFailClosedWhenAClaimPathResolvesToANonStringValue() {
    // given: OidcPrincipalLoader's underlying JSONPath read throws (rather than returning null)
    // specifically when a configured path resolves to a non-string value, e.g. an array. This is
    // the one case that actually reaches OidcBearerPrincipalClassifier's catch block, as opposed to
    // an unresolvable path, which OidcPrincipalLoader itself already reduces to a null claim.
    final OidcConfiguration configuration = new OidcConfiguration();
    configuration.setUsernameClaim("preferred_username");
    configuration.setClientIdClaim("client_id");
    final var classifier = new OidcBearerPrincipalClassifier(configuration);
    final Map<String, Object> claims =
        Map.of("preferred_username", "noopt", "client_id", List.of("not", "a", "string"));

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldTreatAnUnconfiguredClientIdClaimAsClientIdByDefault() {
    // given: an OidcConfiguration with no explicit client-id-claim configured (the out-of-the-box
    // state for an operator who has not set camunda.security.authentication.oidc.client-id-claim).
    // A Keycloak client-credentials token carries a "client_id" claim; the classifier must default
    // to that claim name so an M2M client is correctly recognized without any operator config.
    final var classifier = new OidcBearerPrincipalClassifier(new OidcConfiguration());
    final Map<String, Object> claims = Map.of("client_id", "optimize-api-client");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldSubjectAnM2mTokenToTheCheckWhenItsIdpDoesNotUseTheDefaultClientIdClaim() {
    // given: an M2M token from an IdP that identifies clients via a different claim (e.g. Entra's
    // "azp"), with no client-id-claim override configured. This is the deliberate, documented
    // trade-off of failing closed: such a client is now subjected to the permission check rather
    // than silently exempted, and must either be granted the Optimize permission or have the
    // operator configure the correct claim-id-claim for their IdP.
    final var classifier = new OidcBearerPrincipalClassifier(new OidcConfiguration());
    final Map<String, Object> claims = Map.of("azp", "some-m2m-client-id");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldTreatAnUnconfiguredClassifierAsRequiringTheCheckForAGenuineUserToken() {
    // given: the same unconfigured OidcConfiguration, but a token with no client_id claim at all —
    // OidcConfiguration's own default usernameClaim ("sub") is present on every token, but that
    // alone must not exempt anything from the check (preferUsernameClaim defaults to false), so
    // classification still turns entirely on the absence of client_id.
    final var classifier = new OidcBearerPrincipalClassifier(new OidcConfiguration());
    final Map<String, Object> claims = Map.of("sub", "noopt", "preferred_username", "noopt");

    // when
    final boolean result = classifier.requiresOptimizePermissionCheck(claims);

    // then
    assertThat(result).isTrue();
  }
}
