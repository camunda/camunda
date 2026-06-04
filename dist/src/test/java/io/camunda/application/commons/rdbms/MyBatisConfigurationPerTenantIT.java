/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static io.camunda.configuration.physicaltenants.PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
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
  private static final RdbmsDatabaseIdProvider DATABASE_ID_PROVIDER =
      new RdbmsDatabaseIdProvider(null);

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldBuildIsolatedFactoryAndBundlePerTenantAndExposeDefaultAsSingleton() throws Exception {
    try (final var fixture = wireTenants(DEFAULT_PHYSICAL_TENANT_ID, "tenantb")) {
      assertThat(fixture.factories).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID, "tenantb");
      assertThat(fixture.bundles).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID, "tenantb");
      assertThat(fixture.factories.get(DEFAULT_PHYSICAL_TENANT_ID))
          .isNotSameAs(fixture.factories.get("tenantb"));
      assertThat(MY_BATIS.sqlSessionFactory(fixture.factories))
          .isSameAs(fixture.factories.get(DEFAULT_PHYSICAL_TENANT_ID));
    }
  }

  @Test
  void shouldRouteWriterFactoryByPhysicalTenantId() throws Exception {
    try (final var fixture = wireTenants(DEFAULT_PHYSICAL_TENANT_ID, "tenantb")) {
      final var writerFactory = fixture.writerFactory();

      final var defaultWriters =
          writerFactory.createWriter(new RdbmsWriterConfig.Builder().partitionId(1).build());
      final var tenantBWriters =
          writerFactory.createWriter(
              new RdbmsWriterConfig.Builder().partitionId(1).physicalTenantId("tenantb").build());

      assertThat(defaultWriters.getExecutionQueue())
          .isNotSameAs(tenantBWriters.getExecutionQueue());
    }
  }

  @Test
  void shouldRejectUnknownPhysicalTenantId() throws Exception {
    try (final var fixture = wireTenants(DEFAULT_PHYSICAL_TENANT_ID)) {
      final var writerFactory = fixture.writerFactory();

      assertThatThrownBy(
              () ->
                  writerFactory.createWriter(
                      new RdbmsWriterConfig.Builder()
                          .partitionId(1)
                          .physicalTenantId("unknown")
                          .build()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown")
          .hasMessageContaining(DEFAULT_PHYSICAL_TENANT_ID);
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
    }
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    final var resolver = PhysicalTenantResolver.of(env, new Camunda());
    final var dataSources =
        RdbmsDataSources.of(
            resolver.mapValues(c -> c.getData().getSecondaryStorage().getRdbms()),
            DATABASE_ID_PROVIDER);
    final var factories = MY_BATIS.sqlSessionFactories(dataSources, resolver, DATABASE_ID_PROVIDER);
    final var bundles = MY_BATIS.rdbmsMapperBundles(factories, dataSources);
    return new TenantFixture(dataSources, factories, bundles);
  }

  private record TenantFixture(
      RdbmsDataSources dataSources,
      java.util.Map<String, org.apache.ibatis.session.SqlSessionFactory> factories,
      java.util.Map<String, io.camunda.db.rdbms.write.RdbmsMapperBundle> bundles)
      implements AutoCloseable {

    RdbmsWriterFactory writerFactory() {
      return new RdbmsWriterFactory(bundles, new SimpleMeterRegistry());
    }

    @Override
    public void close() {
      dataSources.close();
    }
  }
}
