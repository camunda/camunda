/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.search.clients.reader.SearchClientReaders;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalTenantSearchClientReadersTest {

  private static final String DEFAULT = "default";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldReturnAllReadersWhenDefaultPresent() {
    // given
    final var defaultReaders = mock(SearchClientReaders.class);
    final var tenantBReaders = mock(SearchClientReaders.class);
    final var holder =
        new PhysicalTenantSearchClientReaders(
            Map.of(DEFAULT, defaultReaders, TENANT_B, tenantBReaders));

    // when
    final var readers = holder.requireDefaultTenant();

    // then
    assertThat(readers)
        .containsOnlyKeys(DEFAULT, TENANT_B)
        .containsEntry(DEFAULT, defaultReaders)
        .containsEntry(TENANT_B, tenantBReaders);
  }

  @Test
  void shouldRejectMissingDefaultPhysicalTenant() {
    // given a holder without the default physical tenant
    final var holder =
        new PhysicalTenantSearchClientReaders(Map.of(TENANT_B, mock(SearchClientReaders.class)));

    // when / then
    assertThatThrownBy(holder::requireDefaultTenant)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing '" + DEFAULT + "' physical tenant");
  }
}
