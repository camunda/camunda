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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchClientConfigurationTest {

  private static final String DEFAULT = "default";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldMergeDisjointHoldersFromMultipleConfigurations() {
    // given two storage configurations, each contributing a distinct physical tenant
    final var defaultReaders = mock(SearchClientReaders.class);
    final var tenantBReaders = mock(SearchClientReaders.class);
    final var globalHolder = new PhysicalTenantSearchClientReaders(Map.of(DEFAULT, defaultReaders));
    final var additionalHolder =
        new PhysicalTenantSearchClientReaders(Map.of(TENANT_B, tenantBReaders));

    // when
    final var merged =
        SearchClientConfiguration.mergeByPhysicalTenant(List.of(globalHolder, additionalHolder));

    // then both tenants are present, each mapped to its own configuration's readers
    assertThat(merged)
        .containsOnlyKeys(DEFAULT, TENANT_B)
        .containsEntry(DEFAULT, defaultReaders)
        .containsEntry(TENANT_B, tenantBReaders);
  }

  @Test
  void shouldMergeSingleHolder() {
    // given only the global configuration is active
    final var defaultReaders = mock(SearchClientReaders.class);
    final var globalHolder = new PhysicalTenantSearchClientReaders(Map.of(DEFAULT, defaultReaders));

    // when
    final var merged = SearchClientConfiguration.mergeByPhysicalTenant(List.of(globalHolder));

    // then
    assertThat(merged).containsExactly(Map.entry(DEFAULT, defaultReaders));
  }

  @Test
  void shouldRejectPhysicalTenantBackedByMultipleConfigurations() {
    // given two configurations both claiming the same physical tenant
    final var globalHolder =
        new PhysicalTenantSearchClientReaders(Map.of(DEFAULT, mock(SearchClientReaders.class)));
    final var conflictingHolder =
        new PhysicalTenantSearchClientReaders(Map.of(TENANT_B, mock(SearchClientReaders.class)));
    final var duplicateHolder =
        new PhysicalTenantSearchClientReaders(Map.of(TENANT_B, mock(SearchClientReaders.class)));

    // when / then
    assertThatThrownBy(
            () ->
                SearchClientConfiguration.mergeByPhysicalTenant(
                    List.of(globalHolder, conflictingHolder, duplicateHolder)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TENANT_B)
        .hasMessageContaining("more than one storage configuration");
  }

  @Test
  void shouldRejectMissingDefaultPhysicalTenant() {
    // given no configuration contributes the default tenant
    final var additionalHolder =
        new PhysicalTenantSearchClientReaders(Map.of(TENANT_B, mock(SearchClientReaders.class)));

    // when / then
    assertThatThrownBy(
            () -> SearchClientConfiguration.mergeByPhysicalTenant(List.of(additionalHolder)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing '" + DEFAULT + "' physical tenant");
  }
}
