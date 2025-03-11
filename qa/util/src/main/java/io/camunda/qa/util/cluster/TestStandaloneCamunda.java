/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.config.operate.OperateProperties;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Represents an instance of the {@link BrokerModuleConfiguration} Spring application.
 *
 * @deprecated Make use of {@link TestSimpleCamundaApplication} together with {@link
 *     io.camunda.qa.util.multidb.MultiDbTest}.
 */
@SuppressWarnings("UnusedReturnValue")
@Deprecated(forRemoval = true)
public final class TestStandaloneCamunda extends TestSpringApplication<TestStandaloneCamunda>
    implements TestGateway<TestStandaloneCamunda>,
        TestStandaloneApplication<TestStandaloneCamunda> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestStandaloneCamunda.class);
  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearchExporter";
  private static final String CAMUNDA_EXPORTER_ID = "camundaExporter";
  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI
  private final BrokerBasedProperties brokerProperties;
  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;
  private final CamundaSecurityProperties securityConfig;
  private final Map<String, Consumer<ExporterCfg>> registeredExporters = new HashMap<>();

  public TestStandaloneCamunda() {
    super(
        CommonsModuleConfiguration.class,
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        BrokerModuleConfiguration.class,
        // test overrides - to control data clean up; (and some components are not installed on
        // Tests)
        TestOperateElasticsearchSchemaManager.class,
        TestOperateOpensearchSchemaManager.class,
        TestTasklistElasticsearchSchemaManager.class,
        TestTasklistOpensearchSchemaManager.class,
        TestOperateSchemaStartup.class,
        TestTasklistSchemaStartup.class,
        IndexTemplateDescriptorsConfigurator.class);

    brokerProperties = new BrokerBasedProperties();

    brokerProperties.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    // set a smaller default log segment size since we pre-allocate, which might be a lot in tests
    // for local development; also lower the watermarks for local testing
    brokerProperties.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    brokerProperties.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    brokerProperties.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    brokerProperties.getExperimental().getConsistencyChecks().setEnableForeignKeyChecks(true);
    brokerProperties.getExperimental().getConsistencyChecks().setEnablePreconditions(true);

    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();
    securityConfig = new CamundaSecurityProperties();
    securityConfig
        .getInitialization()
        .getUsers()
        .add(
            new ConfiguredUser(
                InitializationConfiguration.DEFAULT_USER_USERNAME,
                InitializationConfiguration.DEFAULT_USER_PASSWORD,
                InitializationConfiguration.DEFAULT_USER_NAME,
                InitializationConfiguration.DEFAULT_USER_EMAIL));

    //noinspection resource
    withBean("config", brokerProperties, BrokerBasedProperties.class)
        .withBean("operate-config", operateProperties, OperateProperties.class)
        .withBean("tasklist-config", tasklistProperties, TasklistProperties.class)
        .withBean("security-config", securityConfig, CamundaSecurityProperties.class)
        .withAdditionalProfile(Profile.BROKER)
        .withAdditionalProfile(Profile.OPERATE)
        .withAdditionalProfile(Profile.TASKLIST)
        .withAdditionalInitializer(new WebappsConfigurationInitializer());

    // default exporters
    withRecordingExporter(true).withCamundaExporter();
  }

  @Override
  public TestStandaloneCamunda start() {
    esContainer.start();

    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    operateProperties.getElasticsearch().setUrl(esURL);
    operateProperties.getZeebeElasticsearch().setUrl(esURL);
    tasklistProperties.getElasticsearch().setUrl(esURL);
    tasklistProperties.getZeebeElasticsearch().setUrl(esURL);

    setExportersConfig();
    return super.start();
  }

  @Override
  public TestStandaloneCamunda stop() {
    // clean up ES/OS indices
    LOGGER.info("Stopping standalone camunda test...");
    esContainer.stop();
    return super.stop();
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case COMMAND -> brokerProperties.getNetwork().getCommandApi().getPort();
      case GATEWAY -> brokerProperties.getGateway().getNetwork().getPort();
      case CLUSTER -> brokerProperties.getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  public TestStandaloneCamunda withProperty(final String key, final Object value) {
    // Since the security config is not constructed from the properties, we need to manually update
    // it when we override a property.
    AuthenticationProperties.applyToSecurityConfig(securityConfig, key, value);
    super.withProperty(key, value);
    return this;
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property as it won't resolve from the
    // config bean
    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    withProperty("zeebe.broker.gateway.enable", brokerProperties.getGateway().isEnable());
    withProperty("camunda.database.url", esURL);
    return super.createSpringBuilder();
  }

  public TestRestOperateClient newOperateClient() {
    return new TestRestOperateClient(restAddress());
  }

  public TestRestOperateClient newOperateClient(final String username, final String password) {
    return new TestRestOperateClient(restAddress(), username, password);
  }

  public TestRestTasklistClient newTasklistClient() {
    return new TestRestTasklistClient(restAddress(), getElasticSearchHostAddress());
  }

  @Override
  public TestStandaloneCamunda self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(brokerProperties.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return brokerProperties.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return BrokerHealthActuator.of(monitoringUri().toString());
  }

  @Override
  public boolean isGateway() {
    return brokerProperties.getGateway().isEnable();
  }

  @Override
  public URI grpcAddress() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return TestStandaloneApplication.super.grpcAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public TestStandaloneCamunda withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(brokerProperties.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return brokerProperties.getGateway();
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  public TestStandaloneCamunda withSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    modifier.accept(securityConfig);
    return this;
  }

  /**
   * Enables/disables usage of the recording exporter using {@link #RECORDING_EXPORTER_ID} as its
   * unique ID.
   *
   * @param useRecordingExporter if true, will enable the exporter; if false, will remove it from
   *     the config
   * @return itself for chaining
   */
  public TestStandaloneCamunda withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      registeredExporters.remove(RECORDING_EXPORTER_ID);
      return this;
    }
    return withExporter(
        RECORDING_EXPORTER_ID, cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  /**
   * Enables/disables usage of the elasticsearch exporter using {@link #ELASTICSEARCH_EXPORTER_ID}
   * as its unique ID.
   *
   * @param useElasticsearchExporter if true, will enable the exporter; if false, will remove it
   *     from the config
   * @return itself for chaining
   */
  public TestStandaloneCamunda withElasticsearchExporter(final boolean useElasticsearchExporter) {
    if (!useElasticsearchExporter) {
      registeredExporters.remove(ELASTICSEARCH_EXPORTER_ID);
      return this;
    }
    return withExporter(
        ELASTICSEARCH_EXPORTER_ID,
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(Map.of("url", "http://" + esContainer.getHttpHostAddress()));
        });
  }

  /**
   * Enables usage of the camunda exporter using {@link #CAMUNDA_EXPORTER_ID} as its unique ID.
   *
   * @return itself for chaining
   */
  public TestStandaloneCamunda withCamundaExporter() {
    withExporter(
        CAMUNDA_EXPORTER_ID,
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of("url", "http://" + esContainer.getHttpHostAddress()),
                  "bulk",
                  Map.of("size", 1)));
        });
    return this;
  }

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  @Override
  public TestStandaloneCamunda withExporter(final String id, final Consumer<ExporterCfg> modifier) {
    registeredExporters.merge(id, modifier, (key, cfg) -> cfg.andThen(modifier));
    return this;
  }

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestStandaloneCamunda withBrokerConfig(final Consumer<BrokerBasedProperties> modifier) {
    modifier.accept(brokerProperties);
    return this;
  }

  /** Returns the broker configuration */
  @Override
  public BrokerBasedProperties brokerConfig() {
    return brokerProperties;
  }

  @Override
  public Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return apiAuthenticationMethod();
  }

  private void setExportersConfig() {
    registeredExporters.forEach(
        (id, exporterModifier) -> {
          final var cfg = new ExporterCfg();
          exporterModifier.accept(cfg);
          brokerProperties.getExporters().put(id, cfg);
        });
  }

  /**
   * Sets the broker's working directory, aka its data directory. If a path is given, the broker
   * will not delete it on shutdown.
   *
   * @param directory path to the broker's root data directory
   * @return itself for chaining
   */
  public TestStandaloneCamunda withWorkingDirectory(final Path directory) {
    return withBean(
        "workingDirectory", new WorkingDirectory(directory, false), WorkingDirectory.class);
  }

  public TestStandaloneCamunda withAuthorizationsEnabled() {
    // when using authorizations, api authentication needs to be enforced too
    withAuthenticatedAccess();
    return withSecurityConfig(
        securityConfig -> securityConfig.getAuthorizations().setEnabled(true));
  }

  public String getElasticSearchHostAddress() {
    return esContainer.getHttpHostAddress();
  }

  public TestStandaloneCamunda withMultiTenancyEnabled() {
    return withSecurityConfig(securityConfig -> securityConfig.getMultiTenancy().setEnabled(true));
  }
}
