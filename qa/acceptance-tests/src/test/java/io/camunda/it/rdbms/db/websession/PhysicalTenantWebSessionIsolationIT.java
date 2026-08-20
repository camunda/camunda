/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.websession;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.spi.PhysicalTenantScopedSessionStorePortProvider;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.PersistentWebSessionRdbmsClient;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.ScopedSessionStorePortProvider;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that per-physical-tenant session stores are isolated at rest against real RDBMS (H2).
 *
 * <p>Post ADR-0029 each scope resolves its own single-store {@code SessionStorePort} via the {@link
 * ScopedSessionStorePortProvider}; there is no cross-tenant routing/fan-out adapter. A session
 * written through one tenant's store is invisible to another tenant's store, and survives a restart
 * in its own store.
 */
@Tag("rdbms")
class PhysicalTenantWebSessionIsolationIT {

  private static final String URL_TENANTA =
      "jdbc:h2:mem:ws-isol-tenanta;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String TENANT_A = "tenanta";
  private static final String BASE_TENANT_A = "/physical-tenants/" + TENANT_A;
  private static final String BASE_DEFAULT =
      "/physical-tenants/" + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  private CamundaRdbmsTestApplication app;
  private CamundaRdbmsTestApplication app2;

  @BeforeEach
  void setUp() {
    app = buildApp().start();
  }

  @AfterEach
  void tearDown() {
    if (app2 != null) {
      app2.close();
      app2 = null;
    }
    if (app != null) {
      app.close();
      app = null;
    }
  }

  private static CamundaRdbmsTestApplication buildApp() {
    return new CamundaRdbmsTestApplication(
            RdbmsTestConfiguration.class, SessionStoreConfiguration.class)
        .withH2()
        .withProperty(
            "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.url",
            URL_TENANTA)
        .withProperty(
            "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.username", "sa")
        .withProperty(
            "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.password", "")
        .withProperty(
            "camunda.physical-tenants."
                + TENANT_A
                + ".security.initialization.default-roles.admin.users[0]",
            TENANT_A + "-admin");
  }

  private static PersistentSession session(final String id) {
    final long now = System.currentTimeMillis();
    return new PersistentSession(
        id, now, now, 1800L, Map.of("test", "v".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void shouldIsolateSessionsAcrossPhysicalTenants() {
    // given — per-scope single-store ports for tenant A and the default tenant
    final var provider = app.bean(ScopedSessionStorePortProvider.class);
    final var tenantAStore = provider.forBasePath(BASE_TENANT_A);
    final var defaultStore = provider.forBasePath(BASE_DEFAULT);
    final String id = UUID.randomUUID().toString();

    // when — write through tenant A's store
    tenantAStore.upsert(session(id));

    // then — tenant A can read it, the default store cannot
    final PersistentSession found = tenantAStore.get(id);
    assertThat(found).isNotNull();
    assertThat(found.id()).isEqualTo(id);
    assertThat(defaultStore.get(id)).isNull();
  }

  @Test
  void shouldSurviveRestartInCorrectPhysicalTenantStore() {
    // given — write via app1's tenant A store
    final String id = UUID.randomUUID().toString();
    app.bean(ScopedSessionStorePortProvider.class).forBasePath(BASE_TENANT_A).upsert(session(id));

    // simulate restart: close app1, start fresh app2 with the same H2 URLs
    app.close();
    app = null;
    app2 = buildApp().start();

    // when — read from app2's tenant A store
    final PersistentSession found =
        app2.bean(ScopedSessionStorePortProvider.class).forBasePath(BASE_TENANT_A).get(id);

    // then
    assertThat(found).isNotNull();
    assertThat(found.id()).isEqualTo(id);
  }

  /** Exposes the per-scope session store provider over per-PT RDBMS infra (ADR-0029). */
  @Configuration
  static class SessionStoreConfiguration {

    @Bean
    PhysicalTenantScoped<PersistentWebSessionClient> sessionClientProvider(
        final Map<String, RdbmsMapperBundle> rdbmsMapperBundles) {
      final var byTenant = new LinkedHashMap<String, PersistentWebSessionClient>();
      rdbmsMapperBundles.forEach(
          (tenantId, bundle) -> {
            final var mapper = bundle.persistentWebSessionMapper();
            byTenant.put(
                tenantId,
                new PersistentWebSessionRdbmsClient(
                    new PersistentWebSessionDbReader(mapper),
                    new PersistentWebSessionWriter(mapper)));
          });
      return new PhysicalTenantScopedPersistentWebSessionClient(Map.copyOf(byTenant));
    }

    @Bean
    ScopedSessionStorePortProvider scopedSessionStorePortProvider(
        final PhysicalTenantScoped<PersistentWebSessionClient> sessionClientProvider) {
      return new PhysicalTenantScopedSessionStorePortProvider(sessionClientProvider);
    }
  }
}
