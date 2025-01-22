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
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;

/** Represents an instance of the {@link BrokerModuleConfiguration} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class NewCamundaTestApplication
    extends TestSpringApplication<NewCamundaTestApplication>
    implements TestGateway<NewCamundaTestApplication> {

  private static final Logger LOGGER = LoggerFactory.getLogger(NewCamundaTestApplication.class);
  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearchExporter";
  private static final String CAMUNDA_EXPORTER_ID = "camundaExporter";
  private final BrokerBasedProperties brokerProperties;
  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;
  private final CamundaSecurityProperties securityConfig;
  private final Map<String, Consumer<ExporterCfg>> registeredExporters = new HashMap<>();

  public NewCamundaTestApplication() {
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
  }

  @Override
  public NewCamundaTestApplication start() {
    setExportersConfig();
    return super.start();
  }

  @Override
  public NewCamundaTestApplication stop() {
    // clean up ES/OS indices
    LOGGER.info("Stopping standalone camunda test...");
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
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property as it won't resolve from the
    // config bean
    withProperty("zeebe.broker.gateway.enable", brokerProperties.getGateway().isEnable());
    return super.createSpringBuilder();
  }

  @Override
  public NewCamundaTestApplication self() {
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

    return TestGateway.super.grpcAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public NewCamundaTestApplication withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(brokerProperties.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return brokerProperties.getGateway();
  }

  @Override
  public CamundaClientBuilder newClientBuilder() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Cannot create a new client for this broker, as it does not have an embedded gateway");
    }

    return TestGateway.super.newClientBuilder();
  }

  /** Returns the broker configuration */
  public BrokerBasedProperties brokerConfig() {
    return brokerProperties;
  }

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  public NewCamundaTestApplication withBrokerConfig(
      final Consumer<BrokerBasedProperties> modifier) {
    modifier.accept(brokerProperties);
    return this;
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  public NewCamundaTestApplication withSecurityConfig(
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
  public NewCamundaTestApplication withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      registeredExporters.remove(RECORDING_EXPORTER_ID);
      return this;
    }
    return withExporter(
        RECORDING_EXPORTER_ID, cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  public NewCamundaTestApplication withExporter(
      final String id, final Consumer<ExporterCfg> modifier) {
    registeredExporters.merge(id, modifier, (key, cfg) -> cfg.andThen(modifier));
    return this;
  }

  public NewCamundaTestApplication withElasticsearchSupport(
      final String elasticSearchUrl, final String prefix) {
    operateProperties.getElasticsearch().setUrl(elasticSearchUrl);
    operateProperties.getElasticsearch().setIndexPrefix(prefix);
    operateProperties.getZeebeElasticsearch().setUrl(elasticSearchUrl);
    tasklistProperties.getElasticsearch().setUrl(elasticSearchUrl);
    tasklistProperties.getElasticsearch().setIndexPrefix(prefix);
    tasklistProperties.getZeebeElasticsearch().setUrl(elasticSearchUrl);

    withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      elasticSearchUrl,
                      "indexPrefix",
                      prefix,
                      "type",
                      DatabaseType.ELASTICSEARCH),
                  "index",
                  Map.of("prefix", prefix),
                  "bulk",
                  Map.of("size", 1)));
        });

    withProperty("camunda.database.type", DatabaseType.ELASTICSEARCH);
    withProperty("camunda.database.indexPrefix", prefix);
    withProperty("camunda.database.url", elasticSearchUrl);
    return this;
  }

  public NewCamundaTestApplication withOpensearchSupport(
      final String opensearchUrl,
      final String prefix,
      final String adminUser,
      final String adminPassword) {
    final OperateOpensearchProperties operateOpensearch = operateProperties.getOpensearch();
    operateOpensearch.setUrl(opensearchUrl);
    operateOpensearch.setIndexPrefix(prefix);
    operateOpensearch.setPassword(adminPassword);
    operateOpensearch.setUsername(adminUser);

    tasklistProperties.setDatabase("opensearch");
    final TasklistOpenSearchProperties tasklistOpensearch = tasklistProperties.getOpenSearch();
    tasklistOpensearch.setUrl(opensearchUrl);
    tasklistOpensearch.setIndexPrefix(prefix);
    tasklistOpensearch.setPassword(adminPassword);
    tasklistOpensearch.setUsername(adminUser);

    withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      opensearchUrl,
                      "indexPrefix",
                      prefix,
                      "type",
                      DatabaseType.OPENSEARCH,
                      "username",
                      adminUser,
                      "password",
                      adminPassword),
                  "index",
                  Map.of("prefix", prefix),
                  "bulk",
                  Map.of("size", 1)));
        });

    withProperty("camunda.database.type", DatabaseType.OPENSEARCH);
    withProperty("camunda.operate.database", "opensearch");
    withProperty("camunda.tasklist.database", "opensearch");
    withProperty("camunda.database.indexPrefix", prefix);
    withProperty("camunda.database.username", adminUser);
    withProperty("camunda.database.password", adminPassword);
    withProperty("camunda.database.url", opensearchUrl);
    return this;
  }

  public NewCamundaTestApplication withRdbmsExporter() {
    withProperty("camunda.database.type", DatabaseType.RDBMS);
    withProperty(
        "spring.datasource.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    withProperty("spring.datasource.username", "sa");
    withProperty("spring.datasource.password", "");
    withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    withProperty("logging.level.org.mybatis", "DEBUG");
    withExporter(
        "rdbms",
        cfg -> {
          cfg.setClassName("-");
          cfg.setArgs(Map.of("flushInterval", "0"));
        });
    return this;
  }

  private void setExportersConfig() {
    registeredExporters.forEach(
        (id, exporterModifier) -> {
          final var cfg = new ExporterCfg();
          exporterModifier.accept(cfg);
          brokerProperties.getExporters().put(id, cfg);
        });
  }
}
