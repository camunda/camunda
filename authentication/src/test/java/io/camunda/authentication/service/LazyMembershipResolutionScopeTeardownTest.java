/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Proves what {@link PhysicalTenantMembershipContextPropagator} exists to protect: a {@link
 * CamundaAuthentication} built under one physical tenant resolves its memberships against that
 * tenant even when the lists materialise after the request scope is gone.
 *
 * <p>Serialisation resolves every membership list on the serialising thread, which is what a
 * persistent web session does during Spring Session's commit phase. Without the propagator {@link
 * DefaultMembershipService} would call {@link PhysicalTenantContext#current()} with nothing bound,
 * and fail.
 *
 * <p>Wires the real converter and the real propagator; only {@link MembershipPort} is a stub, so
 * removing the propagation makes these tests fail.
 */
class LazyMembershipResolutionScopeTeardownTest {

  private static final String USERNAME_CLAIM = "preferred_username";
  private static final String CLIENT_ID_CLAIM = "client_id";

  /**
   * Physical tenant observed on each membership lookup, in call order. Unsynchronised on purpose:
   * only one thread ever writes it per test, and in the worker-thread case the task's writes
   * happen-before the {@code Future#get} that precedes the assertions.
   */
  private final List<String> observedPhysicalTenants = new ArrayList<>();

  /**
   * Mirrors {@link DefaultMembershipService}, which routes every lookup via {@code current()} and
   * reads an earlier step's resolved list before that — e.g. {@code roleIds()} reads {@code
   * resolvedGroupIds()} before resolving its own tenant. Touching those fields here forces the same
   * nesting: one decorated supplier resolving from inside another's still-open propagated scope.
   */
  private final MembershipPort recordingMembershipPort =
      new MembershipPort() {
        @Override
        public List<String> mappingRuleIds(final MembershipQuery query) {
          return recordAndReturn("mapping-rule");
        }

        @Override
        public List<String> groupIds(final MembershipQuery query) {
          query.resolvedMappingRuleIds().isEmpty(); // forces mappingRuleIds, nested here
          return recordAndReturn("group");
        }

        @Override
        public List<String> roleIds(final MembershipQuery query) {
          query
              .resolvedGroupIds()
              .isEmpty(); // forces groupIds — which itself forces mappingRuleIds
          return recordAndReturn("role");
        }

        @Override
        public List<String> tenantIds(final MembershipQuery query) {
          query
              .resolvedRoleIds()
              .isEmpty(); // forces roleIds — which itself forces groupIds, mappingRuleIds
          return recordAndReturn("tenant");
        }

        private List<String> recordAndReturn(final String idPrefix) {
          observedPhysicalTenants.add(PhysicalTenantContext.current());
          return List.of(idPrefix + "-1");
        }
      };

  @AfterEach
  void resetRequestScope() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldResolveMembershipsAgainstOriginatingTenantAfterScopeTeardown() throws IOException {
    // given an authentication built while a request for this physical tenant is in scope
    final String physicalTenantId = "tenant-" + UUID.randomUUID();
    final CamundaAuthentication authentication = buildAuthenticationForTenant(physicalTenantId);

    // and the request scope torn down before any membership list is read, as happens when a
    // persistent web session is committed after the request has been dispatched
    RequestContextHolder.resetRequestAttributes();
    assertThat(observedPhysicalTenants).as("no membership resolved eagerly").isEmpty();

    // when the authentication is serialised, forcing every lazy membership list to materialise
    serialize(authentication);

    // then every lookup ran against the tenant captured at build time — never the default, never
    // unresolved. 4 = one per lazy membership field: mapping rules, groups, roles, tenants.
    assertThat(observedPhysicalTenants).hasSize(4).containsOnly(physicalTenantId);
  }

  /**
   * Covers the one decoration the serialisation case above cannot: serialisation resolves groups
   * before mapping rules, and {@code groupIds} reads {@code resolvedMappingRuleIds()}, so mapping
   * rules always materialise nested inside the groups scope and would inherit the tenant even
   * undecorated. Reading them first makes theirs the only scope in play.
   */
  @Test
  void shouldResolveMappingRuleIdsAgainstOriginatingTenantWhenReadFirst() {
    // given an authentication built while a request for this physical tenant is in scope
    final String physicalTenantId = "tenant-" + UUID.randomUUID();
    final CamundaAuthentication authentication = buildAuthenticationForTenant(physicalTenantId);

    // and the request scope torn down before any membership list is read
    RequestContextHolder.resetRequestAttributes();

    // when mapping rules are the first list read, so no other lookup holds an open propagated
    // scope for this one to inherit
    assertThat(authentication.authenticatedMappingRuleIds()).containsExactly("mapping-rule-1");

    // then that lookup ran against the tenant captured at build time, on its own decoration alone
    assertThat(observedPhysicalTenants).containsExactly(physicalTenantId);
  }

  /**
   * Covers what the teardown test above cannot: resolution on a worker thread, which never had a
   * request scope of its own. A propagator that captured the tenant eagerly, instead of binding it
   * inside the deferred supplier, would still pass that test but strand this thread.
   */
  @Test
  void shouldResolveMembershipsAgainstOriginatingTenantOnWorkerThread() throws Exception {
    // given an authentication built while a request for this physical tenant is in scope
    final String physicalTenantId = "tenant-" + UUID.randomUUID();
    final CamundaAuthentication authentication = buildAuthenticationForTenant(physicalTenantId);

    // and that request still in flight on this thread, unlike the teardown case above
    assertThat(RequestContextHolder.getRequestAttributes())
        .as("origin thread still holds the request scope")
        .isNotNull();

    // when a lazy list materialises on a worker thread, which never had a request scope — as when
    // resolution is handed to an async executor mid-request
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final List<String> groupIds;
    try {
      groupIds =
          executor
              .submit(() -> List.copyOf(authentication.authenticatedGroupIds()))
              .get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // then the worker resolved against the tenant captured at build time. 2 = groups plus the
    // mapping rules groupIds forces, both on the worker.
    assertThat(groupIds).containsExactly("group-1");
    assertThat(observedPhysicalTenants).hasSize(2).containsOnly(physicalTenantId);
  }

  /**
   * Builds an authentication the way the OIDC login flow does: real converter, real propagator, run
   * while a request for {@code physicalTenantId} is in scope.
   */
  private CamundaAuthentication buildAuthenticationForTenant(final String physicalTenantId) {
    bindRequestWithPhysicalTenant(physicalTenantId);
    final var converter =
        new LazyTokenClaimsConverter(
            USERNAME_CLAIM,
            CLIENT_ID_CLAIM,
            true,
            recordingMembershipPort,
            new PhysicalTenantMembershipContextPropagator());
    return converter.convert(Map.of(USERNAME_CLAIM, "alice-" + physicalTenantId));
  }

  private void bindRequestWithPhysicalTenant(final String physicalTenantId) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private void serialize(final CamundaAuthentication authentication) throws IOException {
    try (final var bytes = new ByteArrayOutputStream();
        final var out = new ObjectOutputStream(bytes)) {
      out.writeObject(authentication);
    }
  }
}
