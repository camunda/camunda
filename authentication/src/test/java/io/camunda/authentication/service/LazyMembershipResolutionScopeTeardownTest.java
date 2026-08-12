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
 * removing the propagation makes this test fail.
 */
class LazyMembershipResolutionScopeTeardownTest {

  private static final String USERNAME_CLAIM = "preferred_username";
  private static final String CLIENT_ID_CLAIM = "client_id";

  /** Physical tenant observed on each membership lookup, in call order. */
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
    bindRequestWithPhysicalTenant(physicalTenantId);
    final var converter =
        new LazyTokenClaimsConverter(
            USERNAME_CLAIM,
            CLIENT_ID_CLAIM,
            true,
            recordingMembershipPort,
            new PhysicalTenantMembershipContextPropagator());
    final CamundaAuthentication authentication =
        converter.convert(Map.of(USERNAME_CLAIM, "alice-" + physicalTenantId));

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
