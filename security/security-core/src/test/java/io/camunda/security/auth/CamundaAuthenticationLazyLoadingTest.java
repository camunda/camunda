/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.auth.CamundaAuthentication.MembershipData;
import io.camunda.security.auth.CamundaAuthentication.MembershipLoader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CamundaAuthenticationLazyLoadingTest {

  @Test
  void membershipLoaderIsNotCalledBeforeAnyMembershipAccessorIsInvoked() {
    // given
    final var loadCount = new AtomicInteger(0);
    final MembershipLoader loader =
        () -> {
          loadCount.incrementAndGet();
          return new MembershipData(
              List.of("g1"), List.of("r1"), List.of("t1"), List.of("m1"));
        };

    // when — build a lazy auth and access only the principal identity fields
    final var auth =
        CamundaAuthentication.of(b -> b.clientId("client-id").lazyMemberships(loader));
    final var ignored1 = auth.authenticatedClientId();
    final var ignored2 = auth.claims();

    // then — loader must NOT have been called yet
    assertThat(loadCount).hasValue(0);
  }

  @Test
  void membershipLoaderIsCalledOnFirstMembershipAccess() {
    // given
    final var loadCount = new AtomicInteger(0);
    final MembershipLoader loader =
        () -> {
          loadCount.incrementAndGet();
          return new MembershipData(
              List.of("g1"), List.of("r1"), List.of("t1"), List.of("m1"));
        };
    final var auth =
        CamundaAuthentication.of(b -> b.clientId("client-id").lazyMemberships(loader));

    // when
    final var groups = auth.authenticatedGroupIds();

    // then
    assertThat(loadCount).hasValue(1);
    assertThat(groups).containsExactly("g1");
  }

  @Test
  void membershipLoaderIsCalledOnlyOnceAcrossMultipleAccessors() {
    // given
    final var loadCount = new AtomicInteger(0);
    final MembershipLoader loader =
        () -> {
          loadCount.incrementAndGet();
          return new MembershipData(
              List.of("g1"), List.of("r1"), List.of("t1"), List.of("m1"));
        };
    final var auth =
        CamundaAuthentication.of(b -> b.clientId("client-id").lazyMemberships(loader));

    // when — access all four membership lists
    auth.authenticatedGroupIds();
    auth.authenticatedRoleIds();
    auth.authenticatedTenantIds();
    auth.authenticatedMappingRuleIds();

    // then — loader was called exactly once
    assertThat(loadCount).hasValue(1);
  }

  @Test
  void eagerAuthDoesNotInvokeLoader() {
    // given — an auth built without a lazy loader (explicit membership lists)
    final var auth =
        CamundaAuthentication.of(
            b -> b.clientId("client-id").groupIds(List.of("g1")).roleIds(List.of("r1")));

    // when
    final var groups = auth.authenticatedGroupIds();
    final var roles = auth.authenticatedRoleIds();

    // then — values come from the builder, not a deferred load
    assertThat(groups).containsExactly("g1");
    assertThat(roles).containsExactly("r1");
  }

  @Test
  void lazyAuthEqualsEagerAuthWithSameMembershipData() {
    // given
    final MembershipLoader loader =
        () ->
            new MembershipData(List.of("g1"), List.of("r1"), List.of("t1"), List.of("m1"));

    final var lazyAuth =
        CamundaAuthentication.of(b -> b.clientId("client-id").lazyMemberships(loader));
    final var eagerAuth =
        CamundaAuthentication.of(
            b ->
                b.clientId("client-id")
                    .groupIds(List.of("g1"))
                    .roleIds(List.of("r1"))
                    .tenants(List.of("t1"))
                    .mappingRule(List.of("m1")));

    // then — equals triggers the lazy load and compares membership data
    assertThat(lazyAuth).isEqualTo(eagerAuth);
  }
}
