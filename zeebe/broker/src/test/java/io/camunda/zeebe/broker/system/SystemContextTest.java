/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.ConfiguredAuthorization;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg.NodeCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class SystemContextTest {

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(-1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT-1M needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsTooSmall() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofSeconds(1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT1S needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(-1, DataUnit.BYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '-1B'.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsTooLarge() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(3, DataUnit.GIGABYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '3221225472B'.");
  }

  @Test
  void shouldNotThrowExceptionIfSnapshotPeriodIsEqualToOneMinute() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));

    // when
    final var systemContext = initSystemContext(brokerCfg);

    // then
    assertThat(systemContext.getBrokerConfiguration().getData().getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeDoesNotSpecifyAnyPartitions() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define configurations for all partitions such that "
                + "they have 1 replicas, but partition 1 has 0 configured replicas: []");
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeIsNotExhaustive() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.getNodes().add(new NodeCfg());
    brokerCfg.getCluster().setPartitionsCount(2);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define configurations for all partitions such that "
                + "they have 1 replicas, but partition 2 has 0 configured replicas: []");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2})
  void shouldThrowExceptionIfFixedPartitioningSchemeUsesInvalidPartitionId(final int invalidId) {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setPartitionId(invalidId);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define entries with a valid partitionId "
                + "between 1 and 1, but %d was given",
            invalidId);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2})
  void shouldThrowExceptionIfFixedPartitioningSchemeUsesInvalidNodeId(final int invalidId) {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var node = new NodeCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.getNodes().add(node);
    node.setNodeId(invalidId);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme for partition 1 to define nodes with a "
                + "nodeId between 0 and 0, but it was %d",
            invalidId);
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeHasSamePriorities() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var nodes = List.of(new NodeCfg(), new NodeCfg());
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(true);
    brokerCfg.getCluster().setClusterSize(2);
    // required when clusterSize > 1
    brokerCfg.getCluster().setInitialContactPoints(List.of("ContactPoints"));
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setNodes(nodes);
    nodes.get(0).setNodeId(0);
    nodes.get(0).setPriority(1);
    nodes.get(1).setNodeId(1);
    nodes.get(1).setPriority(1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected each node for a partition 1 to have a different priority, but at least two of"
                + " them have the same priorities: [NodeCfg{nodeId=0, priority=1},"
                + " NodeCfg{nodeId=1, priority=1}]");
  }

  @Test
  void shouldNotThrowExceptionIfFixedPartitioningSchemeHasWrongPrioritiesWhenPriorityDisabled() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var nodes = List.of(new NodeCfg(), new NodeCfg());
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(false);
    brokerCfg.getCluster().setClusterSize(2);
    // required when clusterSize > 1
    brokerCfg.getCluster().setInitialContactPoints(List.of("ContactPoints"));
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setNodes(nodes);
    nodes.get(0).setNodeId(0);
    nodes.get(0).setPriority(1);
    nodes.get(1).setNodeId(1);
    nodes.get(1).setPriority(1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg)).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndWrongCert() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(certificate.privateKey())
        .setCertificateChainPath(new File("/tmp/i-dont-exist.crt"));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured network security certificate chain path "
                + "'/tmp/i-dont-exist.crt' to point to a readable file, but it does not");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndWrongKey() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(new File("/tmp/i-dont-exist.key"))
        .setCertificateChainPath(certificate.certificate());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured network security private key path "
                + "'/tmp/i-dont-exist.key' to point to a readable file, but it does not");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndNoPrivateKey() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(null)
        .setCertificateChainPath(certificate.certificate());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid private key path for network security, but none configured");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndNoCert() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(certificate.privateKey())
        .setCertificateChainPath(null);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid certificate chain path for network security, but none "
                + "configured");
  }

  @Test
  void shouldThrowExceptionWhenS3BucketIsNotProvided() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().getBackup().setStore(BackupStoreType.S3);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Bucket name must not be empty");
  }

  @Test
  void shouldThrowExceptionWhenS3IsNotConfigured() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setStore(BackupStoreType.S3);
    backupCfg.getS3().setBucketName("bucket");

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed configuring backup store S3");
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/12678")
  void shouldThrowExceptionWithInvalidExporters() {
    // given
    final var brokerCfg = new BrokerCfg();
    final List<String> exportersNames = Arrays.asList("unknown", "oops", "nope");
    final Map<String, ExporterCfg> exporters = HashMap.newHashMap(exportersNames.size());

    for (final String exporterName : exportersNames) {
      final ExporterCfg exporterCfg = new ExporterCfg();
      exporterCfg.setClassName(null);
      exporterCfg.setJarPath("unknown".equals(exporterName) ? null : exporterName);
      final Map<String, Object> args = HashMap.newHashMap(1);
      args.put("any_arg", 1);
      exporterCfg.setArgs(args);
      exporters.put(exporterName, exporterCfg);
    }
    brokerCfg.setExporters(exporters);

    // then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters ");
  }

  // --- per-PT security config tests ---

  @Test
  void shouldReturnDefaultConfigForDefaultTenant() {
    // given
    final var defaultCfg = EngineSecurityConfigurations.defaultConfig();
    final var ctx =
        initSystemContextWithMap(
            new BrokerCfg(), Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultCfg));

    // when / then
    assertThat(ctx.getSecurityConfiguration()).isSameAs(defaultCfg);
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .securityConfig())
        .isSameAs(defaultCfg);
  }

  @Test
  void shouldThrowForUnknownTenantSecurityConfig() {
    // given
    final var defaultCfg = EngineSecurityConfigurations.defaultConfig();
    final var ctx =
        initSystemContextWithMap(
            new BrokerCfg(), Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultCfg));

    // when / then
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("unknown-tenant"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id")
        .hasMessageContaining("unknown-tenant");
  }

  @Test
  void shouldThrowFromNoArgSecurityConfigWhenDefaultEntryMissing() {
    // given – a context constructed with maps that lack the default-tenant entry
    final var ptaCfg = EngineSecurityConfigurations.unauthenticatedAndUnauthorized();
    final var ctx =
        initSystemContextWithMaps(
            new BrokerCfg(),
            Map.of("pta", ptaCfg),
            Map.of("pta", mock(BrokerRequestAuthorizationConverter.class)));

    // when / then
    assertThatThrownBy(ctx::getSecurityConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("default physical tenant");
  }

  @Test
  void shouldReturnPerTenantConfigWhenRegistered() {
    // given
    final var defaultCfg = EngineSecurityConfigurations.defaultConfig();
    final var ptaCfg = EngineSecurityConfigurations.unauthenticatedAndUnauthorized();
    final var ctx =
        initSystemContextWithMap(
            new BrokerCfg(),
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultCfg, "pta", ptaCfg));

    // when / then
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .securityConfig())
        .isSameAs(defaultCfg);
    assertThat(ctx.getPhysicalTenantEngineContext("pta").securityConfig()).isSameAs(ptaCfg);
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id");
  }

  @Test
  void shouldResolveKnownTenantsFromSingleConfigConstructor() {
    // given – PhysicalTenantIds.DEFAULT only knows the "default" tenant
    final var ctx = initSystemContext(new BrokerCfg());

    // when / then – the single-config ctor populates every known tenant with the supplied config
    assertThat(ctx.getSecurityConfiguration()).isNotNull();
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .securityConfig())
        .isSameAs(ctx.getSecurityConfiguration());
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("any-other"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id");
  }

  @Test
  void shouldReturnPerTenantAuthorizationConverterWhenRegistered() {
    // given
    final var defaultConverter = mock(BrokerRequestAuthorizationConverter.class);
    final var ptConverter = mock(BrokerRequestAuthorizationConverter.class);
    final var ctx =
        initSystemContextWithMaps(
            new BrokerCfg(),
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                EngineSecurityConfigurations.defaultConfig(),
                "pta",
                EngineSecurityConfigurations.unauthenticatedAndUnauthorized()),
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                defaultConverter,
                "pta",
                ptConverter));

    // when / then
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .authorizationConverter())
        .isSameAs(defaultConverter);
    assertThat(ctx.getPhysicalTenantEngineContext("pta").authorizationConverter())
        .isSameAs(ptConverter);
  }

  @Test
  void shouldThrowForUnknownTenantAuthorizationConverter() {
    // given
    final var defaultConverter = mock(BrokerRequestAuthorizationConverter.class);
    final var ctx =
        initSystemContextWithMaps(
            new BrokerCfg(),
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                EngineSecurityConfigurations.defaultConfig()),
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultConverter));

    // when / then
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .authorizationConverter())
        .isSameAs(defaultConverter);
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id");
  }

  @Test
  void shouldResolveKnownTenantsAuthorizationConverterFromSingleConverterConstructor() {
    // given – the single-converter constructor stores the converter under every known tenant
    final var ctx = initSystemContext(new BrokerCfg());

    // when / then
    assertThat(
            ctx.getPhysicalTenantEngineContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .authorizationConverter())
        .isNotNull();
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("any-other"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id");
  }

  @Test
  void shouldValidateAllPerTenantInitializationConfigsAndPassWhenAllValid() {
    // given – both configs have empty (valid) initialization blocks
    final var ctx =
        initSystemContextWithMap(
            new BrokerCfg(),
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                EngineSecurityConfigurations.defaultConfig(),
                "pta",
                EngineSecurityConfigurations.unauthenticatedAndUnauthorized()));

    // when / then – no exception expected
    assertThat(ctx).isNotNull();
  }

  @Test
  void shouldFailValidationWhenAnyPerTenantInitializationConfigIsInvalid() {
    // given – an authorization whose resourceId contains spaces, which the default ID pattern
    // rejects (pattern requires [a-zA-Z0-9_~@.+-]+)
    final var badAuthorization =
        ConfiguredAuthorization.idBased(
            AuthorizationOwnerType.USER,
            "demo",
            AuthorizationResourceType.PROCESS_DEFINITION,
            "has spaces here",
            Set.of(PermissionType.READ));
    final var badInit = new InitializationConfiguration();
    badInit.setAuthorizations(List.of(badAuthorization));
    final var badCfg =
        new EngineSecurityConfig(
            new AuthenticationConfiguration(),
            false,
            false,
            badInit,
            EngineSecurityConfigurations.ID_VALIDATION_PATTERN,
            EngineSecurityConfigurations.GROUP_ID_VALIDATION_PATTERN);

    // when / then
    assertThatThrownBy(
            () ->
                initSystemContextWithMap(
                    new BrokerCfg(), Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, badCfg)))
        .isInstanceOf(IdentityInitializationException.class)
        .hasMessageContaining("authorizations");
  }

  @Test
  void shouldReturnFeatureFlagsForGivenTenant() {
    // given
    final var tenantId = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
    final var flags = FeatureFlags.createDefaultForTests();
    final var ctx =
        new SystemContext(
            SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
            new BrokerCfg(),
            null,
            mock(ActorScheduler.class),
            mock(AtomixCluster.class),
            mock(BrokerClient.class),
            new SimpleMeterRegistry(),
            Map.of(
                tenantId,
                new PhysicalTenantEngineContext(
                    EngineSecurityConfigurations.defaultConfig(),
                    mock(BrokerRequestAuthorizationConverter.class),
                    flags)),
            mock(UserServices.class),
            mock(PasswordEncoder.class),
            mock(JwtDecoder.class),
            (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims,
            mock(SearchClientsProxy.class),
            mock(NodeIdProvider.class),
            PhysicalTenantIds.DEFAULT);

    // when / then
    assertThat(ctx.getPhysicalTenantEngineContext(tenantId).featureFlags()).isSameAs(flags);
  }

  @Test
  void shouldThrowForUnknownPhysicalTenantId() {
    // given
    final var ctx = initSystemContext(new BrokerCfg());

    // when / then
    assertThatThrownBy(() -> ctx.getPhysicalTenantEngineContext("unknown-tenant"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-tenant");
  }

  private SystemContext initSystemContext(final BrokerCfg brokerCfg) {
    return SystemContextTestFactory.singleTenant(
        SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
        brokerCfg,
        null,
        mock(ActorScheduler.class),
        mock(AtomixCluster.class),
        mock(BrokerClient.class),
        new SimpleMeterRegistry(),
        EngineSecurityConfigurations.defaultConfig(),
        mock(UserServices.class),
        mock(PasswordEncoder.class),
        mock(JwtDecoder.class),
        (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims,
        mock(SearchClientsProxy.class),
        mock(BrokerRequestAuthorizationConverter.class),
        mock(NodeIdProvider.class),
        PhysicalTenantIds.DEFAULT);
  }

  private SystemContext initSystemContextWithMap(
      final BrokerCfg brokerCfg, final Map<String, EngineSecurityConfig> configsByTenant) {
    final Map<String, BrokerRequestAuthorizationConverter> convertersByTenant = new HashMap<>();
    configsByTenant
        .keySet()
        .forEach(
            tenantId ->
                convertersByTenant.put(tenantId, mock(BrokerRequestAuthorizationConverter.class)));
    return initSystemContextWithMaps(brokerCfg, configsByTenant, convertersByTenant);
  }

  private SystemContext initSystemContextWithMaps(
      final BrokerCfg brokerCfg,
      final Map<String, EngineSecurityConfig> configsByTenant,
      final Map<String, BrokerRequestAuthorizationConverter> convertersByTenant) {
    final Map<String, PhysicalTenantEngineContext> contextsByTenant = new HashMap<>();
    configsByTenant
        .keySet()
        .forEach(
            tenantId ->
                contextsByTenant.put(
                    tenantId,
                    new PhysicalTenantEngineContext(
                        configsByTenant.get(tenantId),
                        convertersByTenant.get(tenantId),
                        FeatureFlags.createDefaultForTests())));
    return new SystemContext(
        SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
        brokerCfg,
        null,
        mock(ActorScheduler.class),
        mock(AtomixCluster.class),
        mock(BrokerClient.class),
        new SimpleMeterRegistry(),
        contextsByTenant,
        tenantId -> mock(UserServices.class),
        mock(PasswordEncoder.class),
        authConfig -> mock(JwtDecoder.class),
        authConfig -> (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims,
        mock(SearchClientsProxy.class),
        mock(NodeIdProvider.class),
        PhysicalTenantIds.DEFAULT);
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerGossiperHasNegativeValues() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidconfigManagerCfg =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(10).negated(),
                Duration.ofSeconds(10).negated(),
                -1,
                Duration.ofSeconds(1)));
    clusterCfg.setConfigManager(invalidconfigManagerCfg);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("syncDelay must be positive: configured value = %d ms", -10000))
        .hasMessageContaining(
            String.format("syncRequestTimeout must be positive: configured value = %s ms", -10000))
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", -1));
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerGossiperHasZeroValues() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidConfigManagerCfg =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(0), Duration.ofSeconds(0), 0, Duration.ofSeconds(1)));
    clusterCfg.setConfigManager(invalidConfigManagerCfg);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("syncDelay must be positive: configured value = %d ms", 0))
        .hasMessageContaining(
            String.format("syncRequestTimeout must be positive: configured value = %s ms", 0))
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", 0));
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerHasGossipFanoutTooSmall() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidDynamicConfig =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1)));
    clusterCfg.setConfigManager(invalidDynamicConfig);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", 1));
  }

  @Test
  void shouldNotThrowExceptionForDefaultBatchOperationConfig() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();

    // when
    final var systemContext = initSystemContext(brokerCfg);

    // then
    assertThat(
            systemContext
                .getBrokerConfiguration()
                .getExperimental()
                .getEngine()
                .getBatchOperations())
        .isNotNull();
  }

  @Test
  void shouldThrowExceptionIfBatchOperationSchedulerIntervalIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setSchedulerInterval(Duration.ofSeconds(-1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.schedulerInterval must be positive");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationChunkSizeIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getEngine().getBatchOperations().setChunkSize(0);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.chunkSize must be greater than 0");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryPageSizeIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getEngine().getBatchOperations().setQueryPageSize(0);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryPageSize must be greater than 0");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryInClauseSizeIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getEngine().getBatchOperations().setQueryInClauseSize(0);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryInClauseSize must be greater than 0");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryRetryMaxIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getEngine().getBatchOperations().setQueryRetryMax(-1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryRetryMax must be greater than or equal to 0");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryRetryBackoffFactorInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getEngine().getBatchOperations().setQueryRetryBackoffFactor(0);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryRetryBackoffFactor must be greater than or equal to");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryRetryInitialDelayIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setQueryRetryInitialDelay(Duration.ofMillis(-1000));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryRetryInitialDelay must be positive");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryRetryMaxDelayIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setQueryRetryMaxDelay(Duration.ofMillis(-1000));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryRetryMaxDelay must be positive");
  }

  @Test
  void shouldThrowExceptionIfBatchOperationQueryRetryMaxDelayIsLowerThanInitialDelay() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setQueryRetryInitialDelay(Duration.ofMillis(1000));
    brokerCfg
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setQueryRetryMaxDelay(Duration.ofMillis(500));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "experimental.engine.batchOperations.queryRetryMaxDelay must be greater than or equal to the experimental.engine.batchOperations.queryRetryInitialDelay");
  }

  @Test
  void shouldIgnoreInvalidEventTypesForGlobalTaskListeners() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(
            List.of(
                createListenerCfg("test", List.of("creating", "non-existing-type", "assigning"))));

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(1);
    final var listenerConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask().getFirst();
    assertThat(listenerConfig.getEventTypes()).containsExactly("creating", "assigning");
  }

  @Test
  void shouldIgnoreGlobalTaskListenerWithoutJobType() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(
            List.of(
                createListenerCfg("test", List.of("creating")),
                createListenerCfg("", List.of("assigning"))));

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(1);
    final var listenerConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask().getFirst();
    assertThat(listenerConfig.getType()).isEqualTo("test");
    assertThat(listenerConfig.getEventTypes()).containsExactly("creating");
  }

  @Test
  void shouldIgnoreGlobalTaskListenerWithoutEventTypes() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(
            List.of(
                createListenerCfg("test1", List.of("creating")),
                createListenerCfg(
                    "test2",
                    List.of("non-existing-type")), // only invalid event type, should be ignored
                createListenerCfg("test3", List.of())));

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(1);
    final var listenerConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask().getFirst();
    assertThat(listenerConfig.getType()).isEqualTo("test1");
    assertThat(listenerConfig.getEventTypes()).containsExactly("creating");
  }

  @Test
  void shouldIgnoreExtraEventTypesWhenConfiguringGenericGlobalTaskListener() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(List.of(createListenerCfg("test", List.of("creating", "all", "updating"))));

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(1);
    final var listenerConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask().getFirst();
    assertThat(listenerConfig.getType()).isEqualTo("test");
    assertThat(listenerConfig.getEventTypes()).containsExactly("all");
  }

  @Test
  void shouldIgnoreGlobalTaskListenerWithNonNumericalOrNegativeRetries() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(
            List.of(
                createListenerCfg("test1", List.of("creating")), // missing retries
                createListenerCfg("test2", List.of("creating"), "not-a-number"), // invalid retries
                createListenerCfg("test3", List.of("creating"), "-1"), // negative retries
                createListenerCfg("test4", List.of("creating"), "4"))); // valid number of retries

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(2);
    final var listenersConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
    assertThat(listenersConfig.get(0).getType()).isEqualTo("test1");
    assertThat(listenersConfig.get(1).getType()).isEqualTo("test4");
  }

  @Test
  void shouldIgnoreDuplicatedEventTypesWhenConfiguringGenericGlobalTaskListener() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg
        .getExperimental()
        .getEngine()
        .getGlobalListeners()
        .setUserTask(
            List.of(createListenerCfg("test", List.of("creating", "creating", "updating"))));

    // when
    initSystemContext(brokerCfg);

    // then
    assertThat(brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask())
        .hasSize(1);
    final var listenerConfig =
        brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask().getFirst();
    assertThat(listenerConfig.getType()).isEqualTo("test");
    assertThat(listenerConfig.getEventTypes()).containsExactly("creating", "updating");
  }

  @Test
  void shouldNotThrowValidationErrorWhenInitialContactPointsIsNotInACluster() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();
    clusterCfg.setClusterSize(1);
    clusterCfg.setPartitionsCount(1);
    clusterCfg.setInitialContactPoints(List.of());

    // when/then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldThrowValidationErrorWhenInitialContactPointsIsNotSetWHenClustering() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();
    clusterCfg.setClusterSize(2);
    clusterCfg.setPartitionsCount(1);
    clusterCfg.setInitialContactPoints(List.of());

    // when/then
    assertThatThrownBy(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "Initial contact points must be configured when cluster size is greater than 1.");
  }

  @Test
  void shouldThrowValidationErrorWhenBackupScheduleIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath("/tmp");
    backupCfg.setSchedule("invalid-schedule");

    // when/then
    assertThatThrownBy(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid expression for schedule: must be one of CRON, ISO8601, NONE. Given: invalid-schedule");
  }

  @Test
  void shouldThrowValidationErrorWhenBackupRetentionScheduleIsInvalid() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath("/tmp");
    backupCfg.setSchedule("PT10M");
    backupCfg.getRetention().setCleanupSchedule("invalid-schedule");

    // when/then
    assertThatThrownBy(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid expression for schedule: must be one of CRON, ISO8601, NONE. Given: invalid-schedule");
  }

  @Test
  void shouldNotThrowValidationErrorWhenRequiredIsFalse() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(false);

    // when/then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldThrowWhenBackupIsRequiredButStoreIsNone() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.NONE);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Backup is required but no backup store is configured");
  }

  @Test
  void shouldNotThrowWhenBackupIsNotRequiredAndStoreIsNone() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setRequired(false);
    backupCfg.setStore(BackupStoreType.NONE);

    // when - then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldNotThrowValidationErrorWhenRequiredIsFalseWithNoValues() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(false);
    backupCfg.setSchedule("PT10M");
    backupCfg.getRetention().setCleanupSchedule("invalid-schedule");

    // when/then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldNotThrowValidationErrorWhenContinuousIsFalse() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(false);
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath("/tmp");
    backupCfg.setSchedule("PT10M");
    backupCfg.getRetention().setCleanupSchedule("invalid-schedule");

    // when/then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldNotThrowValidationErrorWhenNoScheduleProvidedAndIsRequired() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(false);
    backupCfg.setSchedule(null);

    // when/then
    assertThatNoException().isThrownBy(() -> initSystemContext(brokerCfg));
  }

  @Test
  void shouldThrowValidationErrorWhenNoScheduleProvidedAndIsRequired() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath("/tmp");
    backupCfg.setSchedule(null);

    // when/then
    assertThatThrownBy(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Backup schedule is mandatory, none provided.");
  }

  @Test
  void shouldThrowValidationErrorWhenNoRetentionScheduleProvidedAndIsRequired() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setContinuous(true);
    backupCfg.setRequired(true);
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath("/tmp");
    backupCfg.setSchedule("PT10M");
    backupCfg.getRetention().setCleanupSchedule(null);

    // when/then
    assertThatThrownBy(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Backup retention schedule is mandatory, none provided.");
  }

  private GlobalListenerCfg createListenerCfg(final String type, final List<String> eventTypes) {
    final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
    listenerCfg.setId("GlobalListener_" + type);
    listenerCfg.setType(type);
    listenerCfg.setEventTypes(eventTypes);
    return listenerCfg;
  }

  private GlobalListenerCfg createListenerCfg(
      final String type, final List<String> eventTypes, final String retries) {
    final GlobalListenerCfg listenerCfg = createListenerCfg(type, eventTypes);
    listenerCfg.setRetries(retries);
    return listenerCfg;
  }
}
