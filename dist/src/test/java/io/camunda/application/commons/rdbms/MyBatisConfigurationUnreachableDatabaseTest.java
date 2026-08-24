/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The regression guard for the claim this whole feature rests on: building a physical tenant's
 * RDBMS object graph reaches no database, so a tenant whose database is down is degraded on its own
 * rather than failing the context refresh for every tenant on the node.
 *
 * <p>Nothing in the graph declares that it must not connect, so a metadata lookup added anywhere in
 * it would restore the coupling silently — hence a test that builds the whole graph against
 * addresses nothing is listening on. It deliberately uses URLs Spring recognizes, since a URL that
 * needs {@link RdbmsVendorIdProvider}'s connection fallback carries no such guarantee.
 *
 * <p>The one bean it does not run is the initializer's {@code afterPropertiesSet}: that is where
 * the database is supposed to be reached, and with both tenants down it would hold at the gate
 * forever, which is exactly the designed behaviour. Constructing it is still covered.
 */
@Timeout(60)
class MyBatisConfigurationUnreachableDatabaseTest {

  /**
   * The default tenant carries the root configuration and is synthesized as Elasticsearch unless it
   * is declared, and a node may not mix RDBMS with another storage type — so one of the two has to
   * be the default one.
   */
  private static final String TENANT_A = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  private static final String TENANT_B = "tenantb";

  private final MyBatisConfiguration configuration = new MyBatisConfiguration();

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldBuildEveryTenantsBeansWhileNoDatabaseIsReachable() throws Exception {
    // given - two physical tenants pointed at ports nothing is listening on
    final var resolver = twoUnreachableTenants();

    // when
    try (final var dataSources =
        configuration.rdbmsDataSources(resolver, new SimpleMeterRegistry())) {
      final var sqlSessionFactories = configuration.sqlSessionFactories(dataSources, resolver);
      final var mapperBundles = configuration.rdbmsMapperBundles(sqlSessionFactories, dataSources);
      final var schemaManagerRegistry =
          configuration.rdbmsSchemaManagerRegistry(dataSources, resolver);
      final var migrationStatusProvider =
          configuration.rdbmsSchemaMigrationStatusProvider(dataSources, resolver);

      // then - every tenant has a complete graph, and the vendor came from the URL
      assertThat(dataSources.physicalTenantIds()).containsExactly(TENANT_A, TENANT_B);
      assertThat(sqlSessionFactories).containsOnlyKeys(TENANT_A, TENANT_B);
      assertThat(mapperBundles).containsOnlyKeys(TENANT_A, TENANT_B);
      assertThat(dataSources.vendorPropertiesFor(TENANT_A).databaseId()).isEqualTo("postgresql");
      assertThat(dataSources.vendorPropertiesFor(TENANT_B).databaseId()).isEqualTo("mariadb");
      assertThat(migrationStatusProvider).isNotNull();

      // and - no tenant is claimed ready, since nothing has migrated
      assertThat(schemaManagerRegistry.isInitialized(TENANT_A)).isFalse();
      assertThat(schemaManagerRegistry.isInitialized(TENANT_B)).isFalse();
    }
  }

  private static PhysicalTenantResolver twoUnreachableTenants() {
    final var props = new LinkedHashMap<String, Object>();
    // port 1 rather than a routable one, so an attempt fails immediately instead of hanging
    declareTenant(props, TENANT_A, "jdbc:postgresql://localhost:1/camunda");
    declareTenant(props, TENANT_B, "jdbc:mariadb://localhost:1/camunda");
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", props));
    return PhysicalTenantResolver.of(environment, new Camunda());
  }

  private static void declareTenant(
      final LinkedHashMap<String, Object> props, final String tenantId, final String url) {
    final var tenant = "camunda.physical-tenants." + tenantId + ".";
    props.put(tenant + "data.secondary-storage.type", "rdbms");
    props.put(tenant + "data.secondary-storage.rdbms.url", url);
    props.put(tenant + "data.secondary-storage.rdbms.username", "camunda");
    props.put(tenant + "data.secondary-storage.rdbms.password", "camunda");
    // every explicitly-configured tenant must provide its own initialization block
    props.put(tenant + "security.initialization.default-roles.admin.users[0]", tenantId + "-admin");
  }
}
