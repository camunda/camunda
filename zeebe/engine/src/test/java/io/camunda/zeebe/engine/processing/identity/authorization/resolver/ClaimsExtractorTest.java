/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaimsExtractorTest {

  private MembershipState membershipState;
  private ClaimsExtractor claimsExtractor;

  @BeforeEach
  void setUp() {
    membershipState = mock(MembershipState.class);
    claimsExtractor = new ClaimsExtractor(membershipState);
  }

  @Test
  void shouldExtractUsernameFromClaims() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_USERNAME, "demo-user");

    // when
    final var username = claimsExtractor.getUsername(claims);

    // then
    assertThat(username).hasValue("demo-user");
  }

  @Test
  void shouldReturnEmptyWhenUsernameNotPresent() {
    // when
    final var username = claimsExtractor.getUsername(Collections.emptyMap());

    // then
    assertThat(username).isEmpty();
  }

  @Test
  void shouldExtractUsernameFromAuthorizationRequest() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_USERNAME, "demo-user");
    final var request = mock(AuthorizationRequest.class);
    when(request.claims()).thenReturn(claims);

    // when
    final var username = claimsExtractor.getUsername(request);

    // then
    assertThat(username).hasValue("demo-user");
  }

  @Test
  void shouldExtractClientIdFromClaims() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_CLIENT_ID, "zeebe-client");

    // when
    final var clientId = claimsExtractor.getClientId(claims);

    // then
    assertThat(clientId).hasValue("zeebe-client");
  }

  @Test
  void shouldReturnEmptyWhenClientIdNotPresent() {
    // when
    final var clientId = claimsExtractor.getClientId(Collections.emptyMap());

    // then
    assertThat(clientId).isEmpty();
  }

  @Test
  void shouldExtractClientIdFromAuthorizationRequest() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_CLIENT_ID, "zeebe-client");
    final var request = mock(AuthorizationRequest.class);
    when(request.claims()).thenReturn(claims);

    // when
    final var clientId = claimsExtractor.getClientId(request);

    // then
    assertThat(clientId).hasValue("zeebe-client");
  }

  @Test
  void shouldReturnTrueForAnonymousUser() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var isAnonymous = claimsExtractor.isAuthorizedAnonymousUser(claims);

    // then
    assertThat(isAnonymous).isTrue();
  }

  @Test
  void shouldReturnFalseWhenAnonymousUserClaimNotPresent() {
    // given
    final Map<String, Object> claims = new HashMap<>();

    // when
    final var isAnonymous = claimsExtractor.isAuthorizedAnonymousUser(claims);

    // then
    assertThat(isAnonymous).isFalse();
  }

  @Test
  void shouldReturnFalseWhenAnonymousUserSetToFalse() {
    // given
    final Map<String, Object> claims = Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, false);

    // when
    final var isAnonymous = claimsExtractor.isAuthorizedAnonymousUser(claims);

    // then
    assertThat(isAnonymous).isFalse();
  }

  @Test
  void shouldReturnGroupsFromClaimsWhenPresent() {
    // given
    final var expectedGroups = List.of("group1", "group2", "group3");
    final Map<String, Object> claims = Map.of(Authorization.USER_GROUPS_CLAIMS, expectedGroups);

    // when
    final var groups = claimsExtractor.getGroups(claims, EntityType.USER, "user123");

    // then
    assertThat(groups).containsExactlyElementsOf(expectedGroups);
    verify(membershipState, never()).getMemberships(any(), any(), any());
  }

  @Test
  void shouldFetchGroupsFromMembershipStateWhenNotInClaims() {
    // given
    final var expectedGroups = List.of("group-from-db1", "group-from-db2");
    when(membershipState.getMemberships(EntityType.USER, "user123", RelationType.GROUP))
        .thenReturn(expectedGroups);

    // when
    final var groups =
        claimsExtractor.getGroups(Collections.emptyMap(), EntityType.USER, "user123");

    // then
    assertThat(groups).containsExactlyElementsOf(expectedGroups);
    verify(membershipState).getMemberships(EntityType.USER, "user123", RelationType.GROUP);
  }

  @Test
  void shouldExtractTokenClaimsWhenPresent() {
    // given
    final Map<String, Object> tokenClaims =
        Map.of(
            "sub", "user@example.com",
            "iss", "auth-server",
            "aud", "camunda");
    final Map<String, Object> claims = Map.of(Authorization.USER_TOKEN_CLAIMS, tokenClaims);

    // when
    final var extractedTokenClaims = claimsExtractor.getTokenClaims(claims);

    // then
    assertThat(extractedTokenClaims).containsAllEntriesOf(tokenClaims);
  }

  @Test
  void shouldReturnEmptyMapWhenTokenClaimsNotPresent() {
    // when
    final var tokenClaims = claimsExtractor.getTokenClaims(Collections.emptyMap());

    // then
    assertThat(tokenClaims).isEmpty();
  }
}
