/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SPIKE (ADR-0005) <b>control-plane</b> proof. The control-plane permission check ({@code
 * AuthorizationRepositoryAdapter} -> this {@link PhysicalTenantRoutingAuthorizationReader}) runs on
 * the request thread, where the pre-security filter (ADR-0003) has stamped the physical tenant. So
 * the control-plane resolves the in-context tenant's reader via {@link
 * PhysicalTenantContext#current()}.
 *
 * <p>These tests exercise the <b>real</b> thread-bound context (not a stubbed supplier) to confirm
 * a read is routed to the in-context tenant's reader, falls back to {@code default} when no request
 * is bound, and fails fast for an unconfigured tenant. (The data-plane is proven separately and
 * instance-bound — see {@code CamundaSearchClientsPhysicalTenantResourceAccessControllerTest}.)
 */
final class PhysicalTenantRoutingAuthorizationReaderTest {

  private final AuthorizationReader defaultReader = mock(AuthorizationReader.class);
  private final AuthorizationReader tenantBReader = mock(AuthorizationReader.class);
  private final Map<String, AuthorizationReader> readersByPhysicalTenant =
      Map.of("default", defaultReader, "tenant-b", tenantBReader);

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldRouteReadToPhysicalTenantInContext() {
    // given
    when(tenantBReader.search(any(), any())).thenReturn(SearchQueryResult.empty());
    bindRequestWithPhysicalTenant("tenant-b");
    final var reader = new PhysicalTenantRoutingAuthorizationReader(readersByPhysicalTenant);

    // when
    reader.search(AuthorizationQuery.of(q -> q), ResourceAccessChecks.disabled());

    // then
    verify(tenantBReader).search(any(), any());
    verifyNoInteractions(defaultReader);
  }

  @Test
  void shouldFallBackToDefaultWhenNoRequestBound() {
    // given
    when(defaultReader.search(any(), any())).thenReturn(SearchQueryResult.empty());
    RequestContextHolder.resetRequestAttributes();
    final var reader = new PhysicalTenantRoutingAuthorizationReader(readersByPhysicalTenant);

    // when
    reader.search(AuthorizationQuery.of(q -> q), ResourceAccessChecks.disabled());

    // then
    verify(defaultReader).search(any(), any());
    verifyNoInteractions(tenantBReader);
  }

  @Test
  void shouldFailFastForUnconfiguredPhysicalTenant() {
    // given
    bindRequestWithPhysicalTenant("tenant-x");
    final var reader = new PhysicalTenantRoutingAuthorizationReader(readersByPhysicalTenant);

    // when / then
    assertThatThrownBy(
            () -> reader.search(AuthorizationQuery.of(q -> q), ResourceAccessChecks.disabled()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant-x");
  }

  private static void bindRequestWithPhysicalTenant(final String physicalTenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
