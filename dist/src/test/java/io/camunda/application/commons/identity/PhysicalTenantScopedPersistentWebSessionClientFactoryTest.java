/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalTenantScopedPersistentWebSessionClientFactoryTest {

  @Test
  void shouldBuildRdbmsProviderPerTenant() {
    // given
    final var mockMapper1 = mock(PersistentWebSessionMapper.class);
    final var mockBundle1 = mock(RdbmsMapperBundle.class);
    when(mockBundle1.persistentWebSessionMapper()).thenReturn(mockMapper1);

    final var mockMapper2 = mock(PersistentWebSessionMapper.class);
    final var mockBundle2 = mock(RdbmsMapperBundle.class);
    when(mockBundle2.persistentWebSessionMapper()).thenReturn(mockMapper2);

    final var bundles = Map.of("t1", mockBundle1, "t2", mockBundle2);

    // when
    final PhysicalTenantScopedPersistentWebSessionClient provider =
        PhysicalTenantScopedPersistentWebSessionClientFactory.fromRdbmsMapperBundles(bundles);

    // then
    assertThat(provider.withPhysicalTenant("t1")).isNotNull();
    assertThat(provider.withPhysicalTenant("t2")).isNotNull();
    assertThatThrownBy(() -> provider.withPhysicalTenant("unknown-pt"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldBuildSearchProviderPerTenant() {
    // given
    final var searchClient =
        mock(
            DocumentBasedSearchClient.class,
            withSettings().extraInterfaces(DocumentBasedWriteClient.class));
    final var descriptors = new IndexDescriptors("test", /* isElasticsearch= */ true);

    // when
    final PhysicalTenantScopedPersistentWebSessionClient provider =
        PhysicalTenantScopedPersistentWebSessionClientFactory.fromDocumentSearchClients(
            Map.of("t1", searchClient), Map.of("t1", descriptors));

    // then
    assertThat(provider.withPhysicalTenant("t1")).isNotNull();
  }

  @Test
  void shouldThrowWhenSearchClientHasNoIndexDescriptors() {
    // given
    final var searchClient =
        mock(
            DocumentBasedSearchClient.class,
            withSettings().extraInterfaces(DocumentBasedWriteClient.class));

    // when / then
    assertThatThrownBy(
            () ->
                PhysicalTenantScopedPersistentWebSessionClientFactory.fromDocumentSearchClients(
                    Map.of("missing-pt", searchClient), Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing-pt");
  }

  @Test
  void shouldThrowWhenSearchClientIsNotWriteClient() {
    // given — plain mock, does NOT implement DocumentBasedWriteClient
    final var readOnlyClient = mock(DocumentBasedSearchClient.class);
    final var descriptors = new IndexDescriptors("test", /* isElasticsearch= */ true);

    // when / then
    assertThatThrownBy(
            () ->
                PhysicalTenantScopedPersistentWebSessionClientFactory.fromDocumentSearchClients(
                    Map.of("t1", readOnlyClient), Map.of("t1", descriptors)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("t1")
        .hasMessageContaining("DocumentBasedWriteClient");
  }
}
