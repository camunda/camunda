/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.spring;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.application.commons.search.NativeSearchClientsConfiguration;
import io.camunda.application.commons.search.PhysicalTenantSearchClientReadersConfiguration;
import io.camunda.application.commons.search.SearchClientConfiguration;
import io.camunda.application.commons.search.SearchClientReaderConfiguration;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.connect.tenant.TenantConnectConfigResolver;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import jakarta.annotation.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@SpringJUnitConfig
@TestPropertySource(
    properties = {
      "camunda.physical-tenants.enabled=true",
      "camunda.data.secondary-storage.type=elasticsearch"
    })
@Import({
  NativeSearchClientsConfiguration.class,
  SearchClientReaderConfiguration.class,
  SearchClientConfiguration.class,
  PhysicalTenantSearchClientReadersConfiguration.class,
  PhysicalTenantSearchClientReadersConfigurationIT.TestConfig.class
})
public class PhysicalTenantSearchClientReadersConfigurationIT {

  static final String TENANT_A = "tenant-a";
  static final String TENANT_B = "tenant-b";

  static final long PROCESS_INSTANCE_KEY_A = 1001L;
  static final long PROCESS_INSTANCE_KEY_B = 2002L;

  @Autowired
  @Qualifier("physicalTenantAwareCamundaSearchClients")
  private CamundaSearchClients tenantAwareSearchClients;

  @Autowired private SearchClients searchClients;

  @Resource(name = "physicalTenantScopedIndexDescriptors")
  private Map<String, IndexDescriptors> tenantDescriptors;

  @Test
  void shouldWirePerTenantSearchClientsFromResolver() {
    assertThat(searchClients.esClients()).containsOnlyKeys(TENANT_A, TENANT_B);
    assertThat(searchClients.osClients()).isEmpty();
    assertThat(tenantDescriptors).containsOnlyKeys(TENANT_A, TENANT_B);
  }

  @Test
  void shouldRouteProcessInstanceReadsToCorrectTenantContainer() {
    final var clientsA = anonymous(tenantAwareSearchClients).withPhysicalTenant(TENANT_A);
    final var clientsB = anonymous(tenantAwareSearchClients).withPhysicalTenant(TENANT_B);

    final var processInstancesA = clientsA.searchProcessInstances(ProcessInstanceQuery.of(b -> b));
    final var processInstancesB = clientsB.searchProcessInstances(ProcessInstanceQuery.of(b -> b));

    assertThat(processInstancesA.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_A);
    assertThat(processInstancesB.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_B);
  }

  /**
   * Attaches an anonymous security context so that {@link CamundaSearchClients} can execute a read.
   * Authentication/authorization are not under test here, but the production wiring requires a
   * non-null security context to drive the {@link
   * io.camunda.search.clients.auth.ResourceAccessDelegatingController}.
   */
  private static CamundaSearchClients anonymous(final CamundaSearchClients clients) {
    return clients.withSecurityContext(
        SecurityContext.of(b -> b.withAuthentication(CamundaAuthentication.anonymous())));
  }

  // ---------------------------------------------------------------------------------------------
  // Test-only Spring configuration: supplies what the imported production configs cannot, namely
  // the Testcontainers, the multi-tenant TenantConnectConfigResolver override, and the schema
  // bootstrap. Everything else (per-tenant SearchClients, IndexDescriptors map, executors, readers,
  // and the tenant-aware CamundaSearchClients) is wired by Spring from the production configs.
  // ---------------------------------------------------------------------------------------------

  @Configuration(proxyBeanMethods = false)
  static class TestConfig {

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new JavaTimeModule());

    @Bean(initMethod = "start", destroyMethod = "stop")
    ElasticsearchContainer esTenantA() {
      return TestSearchContainers.createDefeaultElasticsearchContainer();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    ElasticsearchContainer esTenantB() {
      return TestSearchContainers.createDefeaultElasticsearchContainer();
    }

    @Bean
    ConnectConfiguration connectConfiguration(final ElasticsearchContainer esTenantA) {
      return connectConfig(esTenantA, TENANT_A);
    }

    @Bean
    @Primary
    TenantConnectConfigResolver tenantConnectConfigResolverOverride(
        final ConnectConfiguration tenantAConfig, final ElasticsearchContainer esTenantB) {
      return new TenantConnectConfigResolver(
          Map.of(TENANT_A, tenantAConfig, TENANT_B, connectConfig(esTenantB, TENANT_B)));
    }

    /** {@link GatewayRestConfiguration} is a plain POJO; register it as a bean for injection. */
    @Bean
    GatewayRestConfiguration gatewayRestConfiguration() {
      return new GatewayRestConfiguration();
    }

    /**
     * Required only so that {@link CamundaSearchClients} can execute a read; access control is not
     * under test here.
     */
    @Bean
    ResourceAccessController testAnonymousResourceAccessController() {
      return new AnonymousResourceAccessController();
    }

    /**
     * Bootstraps the schema and seeds one process-instance document per tenant after all per-tenant
     * beans are wired. Returned as an {@link InitializingBean} so Spring drives initialization
     * after dependency injection.
     */
    @Bean
    InitializingBean schemaBootstrap(
        final SearchClients searchClients,
        final TenantConnectConfigResolver resolver,
        @Qualifier("physicalTenantScopedIndexDescriptors")
            final Map<String, IndexDescriptors> tenantDescriptors) {
      return () -> {
        for (final var entry : resolver.tenantConfigs().entrySet()) {
          final var tenantId = entry.getKey();
          startSchema(
              entry.getValue(),
              tenantDescriptors.get(tenantId),
              searchClients.esClients().get(tenantId));
        }
        seedProcessInstance(
            searchClients.esClients().get(TENANT_A),
            tenantDescriptors.get(TENANT_A),
            PROCESS_INSTANCE_KEY_A);
        seedProcessInstance(
            searchClients.esClients().get(TENANT_B),
            tenantDescriptors.get(TENANT_B),
            PROCESS_INSTANCE_KEY_B);
      };
    }

    private static ConnectConfiguration connectConfig(
        final ElasticsearchContainer container, final String indexPrefix) {
      final var cfg = new ConnectConfiguration();
      cfg.setUrl("http://" + container.getHttpHostAddress());
      cfg.setIndexPrefix(indexPrefix);
      cfg.setType(DatabaseType.ELASTICSEARCH.toString());
      return cfg;
    }

    private static void startSchema(
        final ConnectConfiguration connect,
        final IndexDescriptors descriptors,
        final ElasticsearchClient esClient) {
      new SchemaManager(
              new ElasticsearchEngineClient(esClient, OBJECT_MAPPER),
              descriptors.indices(),
              descriptors.templates(),
              SearchEngineConfiguration.of(b -> b.connect(connect)),
              OBJECT_MAPPER)
          .startup();
    }

    private static void seedProcessInstance(
        final ElasticsearchClient esClient,
        final IndexDescriptors descriptors,
        final long processInstanceKey)
        throws Exception {
      final var listView = descriptors.get(ListViewTemplate.class);
      final var entity =
          new ProcessInstanceForListViewEntity()
              .setId(String.valueOf(processInstanceKey))
              .setKey(processInstanceKey)
              .setProcessInstanceKey(processInstanceKey)
              .setPartitionId(1)
              .setProcessDefinitionKey(processInstanceKey + 1)
              .setBpmnProcessId("process-" + processInstanceKey)
              .setProcessVersion(1)
              .setState(ProcessInstanceState.ACTIVE)
              .setTenantId(ProcessInstanceForListViewEntity.DEFAULT_TENANT_IDENTIFIER);

      esClient.index(
          b ->
              b.index(listView.getFullQualifiedName())
                  .id(entity.getId())
                  .document(entity)
                  .refresh(Refresh.True));
    }
  }
}
