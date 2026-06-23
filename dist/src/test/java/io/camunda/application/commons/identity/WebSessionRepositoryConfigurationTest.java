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

import io.camunda.authentication.config.spi.SessionStoreAdapter;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class WebSessionRepositoryConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withBean(ConnectConfiguration.class, ConnectConfiguration::new)
          .withBean(
              "rdbmsMapperBundles",
              Map.class,
              () -> {
                final var mockMapper = mock(PersistentWebSessionMapper.class);
                final var mockBundle = mock(RdbmsMapperBundle.class);
                when(mockBundle.persistentWebSessionMapper()).thenReturn(mockMapper);
                return Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockBundle);
              })
          .withBean(PhysicalTenantIds.class, () -> PhysicalTenantIds.DEFAULT)
          .withUserConfiguration(WebSessionRepositoryConfiguration.class)
          .withPropertyValues("camunda.data.secondary-storage.type=rdbms");

  @Test
  void shouldWireHostFatalErrorHandlerWhenPersistentSessionsEnabled() {
    runner
        .withPropertyValues("camunda.security.session.persistent.enabled=true")
        .run(
            ctx ->
                assertThat(ctx)
                    .getBean(
                        "webSessionDeletionUncaughtExceptionHandler",
                        UncaughtExceptionHandler.class)
                    .satisfies(
                        handler ->
                            assertThat(handler.getClass().getSimpleName())
                                .as(
                                    "expected host's FatalErrorHandler-backed handler; got %s",
                                    handler.getClass().getName())
                                .isEqualTo("VirtualMachineErrorHandler")));
  }

  @Test
  void shouldNotRegisterWebSessionBeansWhenPersistentSessionsDisabled() {
    runner
        .withPropertyValues("camunda.security.session.persistent.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean("webSessionDeletionUncaughtExceptionHandler"));
  }

  @Test
  void shouldWirePerTenantClientProviderForRdbms() {
    runner
        .withPropertyValues("camunda.security.session.persistent.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(PhysicalTenantScopedPersistentWebSessionClient.class);
              assertThat(ctx)
                  .getBean(SessionStorePort.class)
                  .isInstanceOf(SessionStoreAdapter.class);
              final var provider =
                  ctx.getBean(PhysicalTenantScopedPersistentWebSessionClient.class);
              assertThat(provider.withPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
                  .as("provider must resolve the configured default physical tenant")
                  .isNotNull();
              assertThatThrownBy(() -> provider.withPhysicalTenant("unknown-pt"))
                  .isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void shouldWirePerTenantClientProviderForElasticsearch() {
    // given — a search client stub that implements both read and write interfaces
    final var searchClient =
        mock(
            DocumentBasedSearchClient.class,
            withSettings().extraInterfaces(DocumentBasedWriteClient.class));
    final var descriptors = new IndexDescriptors("test", /* isElasticsearch= */ true);

    new WebApplicationContextRunner()
        .withBean(ConnectConfiguration.class, ConnectConfiguration::new)
        .withBean(
            "physicalTenantDocumentSearchClients",
            Map.class,
            () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, searchClient))
        .withBean(
            "physicalTenantScopedIndexDescriptors",
            Map.class,
            () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, descriptors))
        .withBean(PhysicalTenantIds.class, () -> PhysicalTenantIds.DEFAULT)
        .withUserConfiguration(WebSessionRepositoryConfiguration.class)
        .withPropertyValues(
            "camunda.data.secondary-storage.type=elasticsearch",
            "camunda.security.session.persistent.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(PhysicalTenantScopedPersistentWebSessionClient.class);
              assertThat(ctx)
                  .getBean(SessionStorePort.class)
                  .isInstanceOf(SessionStoreAdapter.class);
              final var provider =
                  ctx.getBean(PhysicalTenantScopedPersistentWebSessionClient.class);
              assertThat(provider.withPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
                  .as("provider must resolve the configured default physical tenant")
                  .isNotNull();
              assertThatThrownBy(() -> provider.withPhysicalTenant("unknown-pt"))
                  .isInstanceOf(IllegalStateException.class);
            });
  }
}
