/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class MyBatisConfigurationPerTenantIT {

  private static final MyBatisConfiguration MY_BATIS = new MyBatisConfiguration();

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldBuildIsolatedFactoryAndBundlePerTenantAndExposeDefaultAsSingleton() throws Exception {
    try (final var fixture = wireTenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, "tenantb")) {
      assertThat(fixture.factories)
          .containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, "tenantb");
      assertThat(fixture.bundles)
          .containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, "tenantb");
      assertThat(fixture.factories.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
          .isNotSameAs(fixture.factories.get("tenantb"));
    }
  }

  @Test
  void shouldBuildIsolatedWritersPerTenant() throws Exception {
    try (final var fixture = wireTenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, "tenantb")) {
      final var defaultWriters =
          fixture
              .writerFactory(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
              .createWriter(new RdbmsWriterConfig.Builder().partitionId(1).build());
      final var tenantBWriters =
          fixture
              .writerFactory("tenantb")
              .createWriter(new RdbmsWriterConfig.Builder().partitionId(1).build());

      assertThat(defaultWriters.getExecutionQueue())
          .isNotSameAs(tenantBWriters.getExecutionQueue());
    }
  }

  private static TenantFixture wireTenants(final String... tenantIds) throws Exception {
    final var props = new LinkedHashMap<String, Object>();
    for (final var tenantId : tenantIds) {
      final var base = "camunda.physical-tenants." + tenantId + ".data.secondary-storage.";
      props.put(base + "type", "rdbms");
      props.put(
          base + "rdbms.url",
          "jdbc:h2:mem:rdbms-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
      props.put(base + "rdbms.username", "sa");
      props.put(base + "rdbms.password", "");
      // every explicitly-configured tenant must provide its own initialization block
      props.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    final var resolver = PhysicalTenantResolver.of(env, new Camunda());
    final var dataSources =
        RdbmsDataSources.of(resolver.mapValues(c -> c.getData().getSecondaryStorage().getRdbms()));
    final var factories = MY_BATIS.sqlSessionFactories(dataSources, resolver);
    final var bundles = MY_BATIS.rdbmsMapperBundles(factories, dataSources);
    return new TenantFixture(dataSources, factories, bundles);
  }

  private record TenantFixture(
      RdbmsDataSources dataSources,
      java.util.Map<String, org.apache.ibatis.session.SqlSessionFactory> factories,
      java.util.Map<String, io.camunda.db.rdbms.write.RdbmsMapperBundle> bundles)
      implements AutoCloseable {

    RdbmsWriterFactory writerFactory(final String physicalTenantId) {
      return new RdbmsWriterFactory(bundles.get(physicalTenantId), new SimpleMeterRegistry());
    }

    @Override
    public void close() {
      dataSources.close();
    }
  }
}
