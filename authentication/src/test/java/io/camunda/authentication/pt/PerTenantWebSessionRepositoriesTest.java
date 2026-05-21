/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.authentication.session.WebSessionMapper;
import io.camunda.search.clients.PersistentWebSessionClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PerTenantWebSessionRepositoriesTest {

  @Test
  void shouldReturnRepositoryBoundToTenantsClient() {
    // given
    final var tenantaClient = mock(PersistentWebSessionClient.class);
    final var defaultClient = mock(PersistentWebSessionClient.class);
    final var clientsByTenant = Map.of("tenanta", tenantaClient, "default", defaultClient);
    final var registry =
        new PerTenantWebSessionRepositories(
            clientsByTenant, mock(WebSessionMapper.class), mock(HttpServletRequest.class));

    // when
    final var tenantaRepo = registry.forTenant("tenanta");
    final var defaultRepo = registry.forTenant("default");

    // then
    assertThat(tenantaRepo).isNotNull();
    assertThat(defaultRepo).isNotNull();
    assertThat(tenantaRepo).isNotSameAs(defaultRepo);
  }

  @Test
  void shouldFailFastForUnknownTenant() {
    // given
    final var registry =
        new PerTenantWebSessionRepositories(
            Map.of(), mock(WebSessionMapper.class), mock(HttpServletRequest.class));

    // when / then
    assertThatThrownBy(() -> registry.forTenant("ghost"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ghost");
  }
}
