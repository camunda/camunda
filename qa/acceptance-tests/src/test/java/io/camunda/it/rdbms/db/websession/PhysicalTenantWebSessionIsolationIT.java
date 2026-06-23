/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.websession;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.spi.SessionStoreAdapter;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.PersistentWebSessionRdbmsClient;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Tag("rdbms")
class PhysicalTenantWebSessionIsolationIT {

  private static final String URL_TENANTA =
      "jdbc:h2:mem:ws-isol-tenanta;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_TENANT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  private CamundaRdbmsTestApplication app;
  private CamundaRdbmsTestApplication app2;

  @BeforeEach
  void setUp() {
    app = buildApp().start();
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
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

  private static void setRequestContext(final String tenantId) {
    final var req = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(req, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
  }

  private static PersistentSession session(final String id) {
    final long now = System.currentTimeMillis();
    return new PersistentSession(id, now, now, 1800L, Map.of("test", "v".getBytes()));
  }

  @Test
  void shouldIsolateSessionsAcrossPhysicalTenants() {
    // given
    final var adapter = app.bean(SessionStorePort.class);
    final String id = UUID.randomUUID().toString();
    final PersistentSession session = session(id);

    // when — write as tenanta
    setRequestContext(TENANT_A);
    adapter.upsert(session);

    // then — tenanta can read it
    final PersistentSession found = adapter.get(id);
    assertThat(found).isNotNull();
    assertThat(found.id()).isEqualTo(id);

    // and — default cannot read it
    setRequestContext(DEFAULT_TENANT);
    assertThat(adapter.get(id)).isNull();
  }

  @Test
  void shouldSurviveRestartInCorrectPhysicalTenantStore() {
    // given — write via app1
    final var adapter1 = app.bean(SessionStorePort.class);
    final String id = UUID.randomUUID().toString();

    setRequestContext(TENANT_A);
    adapter1.upsert(session(id));

    // simulate restart: close app1, start fresh app2 with same H2 URLs
    app.close();
    app = null;
    app2 = buildApp().start();

    // when — read from app2 as tenanta
    final var adapter2 = app2.bean(SessionStorePort.class);
    setRequestContext(TENANT_A);
    final PersistentSession found = adapter2.get(id);

    // then
    assertThat(found).isNotNull();
    assertThat(found.id()).isEqualTo(id);
  }

  @Test
  void shouldAggregateGetAllAcrossAllPhysicalTenants() {
    // given — one session per PT
    final var adapter = app.bean(SessionStorePort.class);
    final String defaultId = UUID.randomUUID().toString();
    final String tenantaId = UUID.randomUUID().toString();

    setRequestContext(DEFAULT_TENANT);
    adapter.upsert(session(defaultId));

    setRequestContext(TENANT_A);
    adapter.upsert(session(tenantaId));

    // when — off-request getAll
    RequestContextHolder.resetRequestAttributes();
    final List<PersistentSession> all = adapter.getAll();

    // then — both sessions present (may contain more from prior tests)
    assertThat(all).extracting(PersistentSession::id).contains(defaultId, tenantaId);
  }

  @Test
  void shouldFanOutDeleteAcrossAllPhysicalTenantsWhenOffRequest() {
    // given — write same session ID to both PT stores
    final var adapter = app.bean(SessionStorePort.class);
    final String id = UUID.randomUUID().toString();
    final PersistentSession s = session(id);

    setRequestContext(DEFAULT_TENANT);
    adapter.upsert(s);

    setRequestContext(TENANT_A);
    adapter.upsert(s);

    // when — off-request delete
    RequestContextHolder.resetRequestAttributes();
    adapter.delete(id);

    // then — gone from both stores
    setRequestContext(DEFAULT_TENANT);
    assertThat(adapter.get(id)).isNull();

    setRequestContext(TENANT_A);
    assertThat(adapter.get(id)).isNull();
  }

  /** Wires {@link SessionStorePort} directly from per-PT RDBMS infra, no condition gating. */
  @Configuration
  static class SessionStoreConfiguration {

    @Bean
    SessionStorePort sessionStorePort(
        final Map<String, RdbmsMapperBundle> rdbmsMapperBundles,
        final PhysicalTenantIds physicalTenants) {
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
      final var provider = new PhysicalTenantScopedPersistentWebSessionClient(Map.copyOf(byTenant));
      return new SessionStoreAdapter(provider, physicalTenants);
    }
  }
}
