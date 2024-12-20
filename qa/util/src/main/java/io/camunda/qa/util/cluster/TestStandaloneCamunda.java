/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.application.Profile;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * This starts the whole camunda stack:
 *
 * <p>
 *
 * <ul>
 *   <li>Broker (see inherited TestStandaloneBroker)
 *   <li>Operate
 *   <li>Tasklist
 *   <li>Dependent database container (Elasticsearch) if needed. For RDBMS, H2 is used inmemory.
 * </ul>
 *
 * <p>
 */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneCamunda extends TestStandaloneBroker {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestStandaloneCamunda.class);

  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearchExporter";
  private static final String CAMUNDA_EXPORTER_ID = "camundaExporter";

  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;
  private final Map<String, Consumer<ExporterCfg>> registeredExporters = new HashMap<>();

  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI

  public TestStandaloneCamunda() {
    // BrokerModuleConfiguration and is already configured in parent
    super(
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        // test overrides - to control data clean up; (and some components are not installed on
        // Tests)
        TestOperateElasticsearchSchemaManager.class,
        TestTasklistElasticsearchSchemaManager.class,
        TestOperateSchemaStartup.class,
        TestTasklistSchemaStartup.class,
        IndexTemplateDescriptorsConfigurator.class);

    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();

    //noinspection resource
    withBean("operate-config", operateProperties, OperateProperties.class)
        .withBean("tasklist-config", tasklistProperties, TasklistProperties.class)
        .withAdditionalProfile(Profile.BROKER)
        .withAdditionalProfile(Profile.OPERATE)
        .withAdditionalProfile(Profile.TASKLIST)
        .withAdditionalInitializer(new WebappsConfigurationInitializer());

    // default exporters
    withRecordingExporter(true);
    withCamundaExporter();
  }

  @Override
  public TestStandaloneCamunda start() {
    startElasticsearchAndSetConfig();

    // Start broker
    super.start();

    return this;
  }

  @Override
  public TestStandaloneCamunda stop() {
    // clean up ES/OS indices
    LOGGER.info("Stopping standalone camunda test...");
    esContainer.stop();

    // Stop broker
    super.stop();
    return this;
  }

  @Override
  public TestStandaloneCamunda withProperty(final String key, final Object value) {
    super.withProperty(key, value);
    return this;
  }

  @Override
  public TestStandaloneCamunda self() {
    return this;
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestStandaloneCamunda withSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    super.withSecurityConfig(modifier);
    return this;
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
  public TestStandaloneCamunda withAdditionalProfile(final Profile profile) {
    super.withAdditionalProfile(profile);
    return this;
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
    withExporter(
        ELASTICSEARCH_EXPORTER_ID,
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(Map.of("url", "http://" + esContainer.getHttpHostAddress()));
        });
    return this;
  }

  /**
   * Enables usage of the camunda exporter using {@link #CAMUNDA_EXPORTER_ID} as its unique ID.
   *
   * @return itself for chaining
   */
  public TestStandaloneCamunda withCamundaExporter() {
    super.withCamundaExporter(getElasticSearchHostAddress());
    return this;
  }

  public TestStandaloneCamunda withAuthorizationsEnabled() {
    withSecurityConfig(securityConfig -> securityConfig.getAuthorizations().setEnabled(true));
    return this;
  }

  public String getElasticSearchHostAddress() {
    return esContainer.getHttpHostAddress();
  }

  public TestStandaloneCamunda withMultiTenancyEnabled() {
    return withSecurityConfig(securityConfig -> securityConfig.getMultiTenancy().setEnabled(true));
  }

  private void startElasticsearchAndSetConfig() {
    esContainer.start();

    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    operateProperties.getElasticsearch().setUrl(esURL);
    operateProperties.getZeebeElasticsearch().setUrl(esURL);
    tasklistProperties.getElasticsearch().setUrl(esURL);
    tasklistProperties.getZeebeElasticsearch().setUrl(esURL);

    withProperty("camunda.database.url", esURL);
  }
}
