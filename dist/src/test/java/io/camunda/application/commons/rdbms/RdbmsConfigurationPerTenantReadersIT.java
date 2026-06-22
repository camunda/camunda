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

import io.camunda.application.commons.search.PhysicalTenantResourceAccessControllers;
import io.camunda.application.commons.search.PhysicalTenantSearchClientReaders;
import io.camunda.application.commons.search.SearchClientConfiguration;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.security.core.authz.ResourceAccessController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@TestPropertySource(properties = {"camunda.data.secondary-storage.type=rdbms"})
@Import({
  MyBatisConfiguration.class,
  RdbmsConfiguration.class,
  SearchClientConfiguration.class,
  RdbmsConfigurationPerTenantReadersIT.TestConfig.class
})
class RdbmsConfigurationPerTenantReadersIT {

  static final String TENANT_B = "tenantb";

  static final long PROCESS_INSTANCE_KEY_DEFAULT = 42L;
  static final long PROCESS_INSTANCE_KEY_B = 99L;

  @Autowired private CamundaSearchClients tenantAwareSearchClients;
  @Autowired private RdbmsWriterFactory writerFactory;

  @Test
  void shouldRouteProcessInstanceReadsToCorrectTenantDatabase() {
    // given a process instance written into each physical tenant's own database
    writeProcessInstance(DEFAULT_PHYSICAL_TENANT_ID, PROCESS_INSTANCE_KEY_DEFAULT);
    writeProcessInstance(TENANT_B, PROCESS_INSTANCE_KEY_B);

    final var clientsDefault =
        anonymous(tenantAwareSearchClients).withPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID);
    final var clientsB = anonymous(tenantAwareSearchClients).withPhysicalTenant(TENANT_B);

    // when
    final var instancesDefault =
        clientsDefault.searchProcessInstances(ProcessInstanceQuery.of(b -> b));
    final var instancesB = clientsB.searchProcessInstances(ProcessInstanceQuery.of(b -> b));

    // then each tenant's reads see only that tenant's own process instance
    assertThat(instancesDefault.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_DEFAULT);
    assertThat(instancesB.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_B);
  }

  private void writeProcessInstance(final String physicalTenantId, final long processInstanceKey) {
    final var writers =
        writerFactory.createWriter(
            new RdbmsWriterConfig.Builder()
                .partitionId(1)
                .physicalTenantId(physicalTenantId)
                .build());
    writers
        .getProcessInstanceWriter()
        .create(
            new ProcessInstanceDbModelBuilder()
                .processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(processInstanceKey)
                .processDefinitionKey(processInstanceKey)
                .processDefinitionId("process-" + processInstanceKey)
                .state(ProcessInstanceState.ACTIVE)
                .startDate(OffsetDateTime.now())
                .version(1)
                .tenantId("<default>")
                .partitionId(1)
                .parentProcessInstanceKey(-1L)
                .parentElementInstanceKey(-1L)
                .numIncidents(0)
                .build());
    writers.flush();
  }

  private static CamundaSearchClients anonymous(final CamundaSearchClients clients) {
    return clients.withSecurityContext(
        SecurityContext.of(b -> b.withAuthentication(CamundaAuthentication.anonymous())));
  }

  @Configuration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    MockEnvironment mockEnvironment() {
      final var env = new MockEnvironment();
      configureTenant(env, DEFAULT_PHYSICAL_TENANT_ID);
      configureTenant(env, TENANT_B);
      return env;
    }

    @Bean
    @Primary
    PhysicalTenantResolver physicalTenantResolverOverride(final MockEnvironment environment) {
      return PhysicalTenantResolver.of(environment, new Camunda());
    }

    /** Required by {@link RdbmsConfiguration} for the query/metrics configuration of readers. */
    @Bean
    Camunda camunda() {
      return new Camunda();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    /**
     * Provides an anonymous controller per physical tenant so {@link CamundaSearchClients} can
     * execute reads; access control is not under test here.
     */
    @Bean
    PhysicalTenantResourceAccessControllers testPhysicalTenantResourceAccessControllers(
        final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
      return new PhysicalTenantResourceAccessControllers(
          physicalTenantSearchClientReaders.readersByPhysicalTenant().keySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      tenantId -> tenantId,
                      tenantId ->
                          (ResourceAccessController) new AnonymousResourceAccessController())));
    }

    private static void configureTenant(final MockEnvironment env, final String tenantId) {
      final var base = "camunda.physical-tenants." + tenantId + ".data.secondary-storage.";
      env.setProperty(base + "type", "rdbms");
      env.setProperty(
          base + "rdbms.url",
          "jdbc:h2:mem:rdbms-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
      env.setProperty(base + "rdbms.username", "sa");
      env.setProperty(base + "rdbms.password", "");
      env.setProperty(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
  }
}
