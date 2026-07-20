/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * An explicitly declared root {@code camunda.data.exporters.camundaexporter} entry must not
 * override a physical tenant's autoconfigured exporter connection: the autoconfigured entry is
 * derived from the tenant's own secondary-storage properties (ADR-0008 §1), which is what keeps a
 * tenant's exported data inside its own storage. Exercises the full production assembly — Spring
 * property binding via {@link UnifiedConfigurationModule}, per-tenant resolution, and {@link
 * SystemContextLoader#createSystemContext()} — with only broker infrastructure mocked.
 */
final class PhysicalTenantCamundaExporterIsolationTest {

  @TempDir private Path workingDirectory;

  @Test
  void shouldDeriveTenantCamundaExporterFromTenantStorageDespiteExplicitRootExporter() {
    // given a root-declared explicit camundaexporter pinning the root connection, and a tenant
    // whose secondary storage overrides the index prefix
    new ApplicationContextRunner()
        .withUserConfiguration(UnifiedConfigurationModule.class)
        .withPropertyValues(
            "spring.profiles.active=broker",
            "camunda.data.secondary-storage.type=elasticsearch",
            "camunda.data.secondary-storage.elasticsearch.url=http://root-es:9200",
            "camunda.data.secondary-storage.elasticsearch.index-prefix=root",
            "camunda.data.exporters.camundaexporter.class-name=io.camunda.exporter.CamundaExporter",
            "camunda.data.exporters.camundaexporter.args.connect.url=http://root-es:9200",
            "camunda.data.exporters.camundaexporter.args.connect.indexPrefix=root",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix=tenanta",
            "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]=tenanta-admin")
        .run(
            context -> {
              // when the broker assembles its per-tenant system context from the bound beans
              final SystemContext systemContext =
                  newLoader(
                          context.getBean(UnifiedConfiguration.class),
                          context.getBean(BrokerBasedProperties.class),
                          context.getBean(PhysicalTenantResolver.class))
                      .createSystemContext();

              // then the tenant's camundaexporter connection comes from the tenant's secondary
              // storage, not from the inherited root exporter entry
              final Map<String, Object> args =
                  systemContext
                      .getPhysicalTenantContext("tenanta")
                      .config()
                      .getExporters()
                      .get("camundaexporter")
                      .getArgs();
              assertThat(argAt(args, "connect", "indexPrefix")).isEqualTo("tenanta");
              assertThat(argAt(args, "connect", "url")).isEqualTo("http://root-es:9200");
            });
  }

  private SystemContextLoader newLoader(
      final UnifiedConfiguration unifiedConfiguration,
      final BrokerBasedProperties rootBrokerCfg,
      final PhysicalTenantResolver physicalTenantResolver) {
    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(new NodeInstance(0, Version.zero()));

    return new SystemContextLoader()
        .withShutdownTimeout(SystemContext.DEFAULT_SHUTDOWN_TIMEOUT)
        .withRootBrokerCfg(rootBrokerCfg)
        .withRootCamunda(unifiedConfiguration.getCamunda())
        .withActorScheduler(mock(ActorScheduler.class))
        .withCluster(mock(AtomixCluster.class))
        .withBrokerClient(mock(BrokerClient.class))
        .withMeterRegistry(new SimpleMeterRegistry())
        .withPhysicalTenantResolver(physicalTenantResolver)
        .withUserServicesForTenant(tenantId -> mock(UserServices.class))
        .withPasswordEncoder(mock(PasswordEncoder.class))
        .withJwtDecoderFactory(authentication -> mock(JwtDecoder.class))
        .withOidcClaimsProviderFactory(
            authentication -> (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims)
        .withSearchClientsProxy(mock(SearchClientsProxy.class))
        .withNodeIdProvider(nodeIdProvider)
        .withWorkingDirectory(workingDirectory)
        .withExporterDescriptors(null);
  }

  @SuppressWarnings("unchecked")
  private static Object argAt(final Map<String, Object> args, final String... path) {
    Object current = args;
    for (final String key : path) {
      current = ((Map<String, Object>) current).get(key);
    }
    return current;
  }
}
