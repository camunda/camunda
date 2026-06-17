/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessController;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPIKE (ADR-0005) data-plane proof test: verifies that {@link CamundaSearchClients} selects the
 * per-PT {@link ResourceAccessController} when {@link
 * CamundaSearchClients#withPhysicalTenant(String)} is called, so the authorization scope check
 * follows the search client's physical tenant — not a thread-local — and therefore works correctly
 * even when the search runs off the request thread (e.g. inside the engine).
 */
class CamundaSearchClientsPhysicalTenantResourceAccessControllerTest {

  @Test
  void shouldRouteResourceAccessCheckToPhysicalTenantInContext() {
    // given
    final var readersDefault = mock(SearchClientReaders.class);
    final var readersB = mock(SearchClientReaders.class);
    final var racDefault = mock(ResourceAccessController.class);
    final var racB = mock(ResourceAccessController.class);

    when(racDefault.doSearch(any(), any())).thenReturn(SearchQueryResult.empty());
    when(racB.doSearch(any(), any())).thenReturn(SearchQueryResult.empty());

    final var clients =
        new CamundaSearchClients(
            Map.of("default", readersDefault, "tenant-b", readersB),
            Map.of("default", racDefault, "tenant-b", racB));

    // when — switch to tenant-b before issuing the search
    clients.withPhysicalTenant("tenant-b").searchAuthorizations(AuthorizationQuery.of(q -> q));

    // then — only the tenant-b controller must be invoked; the default controller must not be
    // touched
    verify(racB).doSearch(any(), any());
    verifyNoInteractions(racDefault);
  }

  @Test
  void shouldFailFastWhenNoPhysicalTenantScoped() {
    // given — unscoped base instance: withPhysicalTenant was never called
    final var readersDefault = mock(SearchClientReaders.class);
    final var readersB = mock(SearchClientReaders.class);
    final var racDefault = mock(ResourceAccessController.class);
    final var racB = mock(ResourceAccessController.class);

    final var clients =
        new CamundaSearchClients(
            Map.of("default", readersDefault, "tenant-b", readersB),
            Map.of("default", racDefault, "tenant-b", racB));

    // when / then — any read on the unscoped base must fail fast; silently reading "default" is the
    // bug class we are eliminating (ADR-0005 B.3)
    assertThatThrownBy(() -> clients.searchAuthorizations(AuthorizationQuery.of(q -> q)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("withPhysicalTenant");
  }
}
