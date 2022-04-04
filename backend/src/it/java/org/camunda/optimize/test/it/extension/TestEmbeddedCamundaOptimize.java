/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.rest.engine.PlatformEngineContextFactory;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.archive.ProcessInstanceArchivingService;
import org.camunda.optimize.service.cleanup.CleanupScheduler;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.events.rollover.EventIndexRolloverService;
import org.camunda.optimize.service.events.rollover.ExternalProcessVariableIndexRolloverService;
import org.camunda.optimize.service.identity.PlatformUserIdentityCache;
import org.camunda.optimize.service.identity.PlatformUserTaskIdentityCache;
import org.camunda.optimize.service.telemetry.TelemetryScheduler;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * This class is wrapper around the embedded optimize to ensure
 * only one instance is used for all tests. Also makes sure the
 * configuration is reloadConfiguration after each test.
 */
public class TestEmbeddedCamundaOptimize extends EmbeddedCamundaOptimize {

  private static final Logger logger = LoggerFactory.getLogger(TestEmbeddedCamundaOptimize.class);
  private static final ObjectMapper configObjectMapper = new ObjectMapper().registerModules(
    new JavaTimeModule(), new Jdk8Module()
  );

  public static final String DEFAULT_USERNAME = "demo";
  public static final String DEFAULT_PASSWORD = "demo";
  private static final String DEFAULT_CONTEXT_LOCATION = "org.camunda.optimize.SpringDefaultITConfig";

  private static TestEmbeddedCamundaOptimize testOptimizeInstance;
  /**
   * This configuration is stored the first time optimize is started
   * and restored before each test, so you can adapt the test
   * to your custom configuration.
   */
  private static String serializedDefaultConfiguration;

  /**
   * Uses the singleton pattern to ensure there is only one
   * optimize instance for all tests.
   */
  public static TestEmbeddedCamundaOptimize getInstance() {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize(DEFAULT_CONTEXT_LOCATION);
    }
    return testOptimizeInstance;
  }

  /**
   * If instance is not initialized, initialize it from specific context. Otherwise
   * return existing instance.
   *
   * @param contextLocation - must be not null
   * @return static instance of embedded Optimize
   */
  public static TestEmbeddedCamundaOptimize getInstance(String contextLocation) {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize(contextLocation);
    }
    return testOptimizeInstance;
  }

  private TestEmbeddedCamundaOptimize(String contextLocation) {
    super(contextLocation);
  }

  public void start() throws Exception {
    if (!testOptimizeInstance.isOptimizeStarted()) {
      testOptimizeInstance.startOptimize();
      if (isThisTheFirstTimeOptimizeWasStarted()) {
        // store the default configuration to restore it later
        serializedDefaultConfiguration =
          configObjectMapper.writeValueAsString(testOptimizeInstance.getConfigurationService());
      }
      resetConfiguration();
      reloadConfiguration();
    }
  }

  private boolean isThisTheFirstTimeOptimizeWasStarted() {
    return serializedDefaultConfiguration == null;
  }

  public void destroy() throws Exception {
    testOptimizeInstance.destroyOptimize();
    testOptimizeInstance = null;
  }

  @Override
  protected ConfigurationService constructConfigurationService() {
    return IntegrationTestConfigurationUtil.createItConfigurationService();
  }

  public void resetConfiguration() throws IOException {
    logger.info("resetting config, parsing defaultconfig and copying properties");
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(
      configObjectMapper
        .readValue(serializedDefaultConfiguration, ConfigurationService.class),
      testOptimizeInstance.getConfigurationService()
    );
    logger.info("done resetting config");
  }

  @SneakyThrows
  public void reloadConfiguration() {
    // reset engine context factory first to ensure we have new clients before reinitializing any other object
    // as they might make use of the engine client
    final EngineContextFactory engineContextFactory =
      getApplicationContext().getBean(PlatformEngineContextFactory.class);
    engineContextFactory.close();
    engineContextFactory.init();

    final Map<String, ?> refreshableServices = getApplicationContext().getBeansOfType(ConfigurationReloadable.class);
    for (Map.Entry<String, ?> entry : refreshableServices.entrySet()) {
      Object beanRef = entry.getValue();
      if (beanRef instanceof ConfigurationReloadable) {
        ConfigurationReloadable reloadable = (ConfigurationReloadable) beanRef;
        reloadable.reloadConfiguration(getApplicationContext());
      }
    }

    // warmup the elastic client with default options (to not make use of plugins)
    // this is done to fully initialize the client as the client does a version validation on the first request
    getOptimizeElasticClient().getHighLevelClient().info(RequestOptions.DEFAULT);
  }

  protected ApplicationContext getApplicationContext() {
    return testOptimizeInstance.getOptimizeApplicationContext();
  }

  public ConfigurationService getConfigurationService() {
    return getApplicationContext().getBean(ConfigurationService.class);
  }

  public CleanupScheduler getCleanupService() {
    return getApplicationContext().getBean(CleanupScheduler.class);
  }

  public TelemetryScheduler getTelemetryService() {
    return getApplicationContext().getBean(TelemetryScheduler.class);
  }

  public ProcessInstanceArchivingService getProcessInstanceArchivingService() {
    return getApplicationContext().getBean(ProcessInstanceArchivingService.class);
  }

  public PlatformUserIdentityCache getPlatformUserIdentityCache() {
    return getApplicationContext().getBean(PlatformUserIdentityCache.class);
  }

  public PlatformUserTaskIdentityCache getPlatformUserTaskIdentityCache() {
    return getApplicationContext().getBean(PlatformUserTaskIdentityCache.class);
  }

  public EventIndexRolloverService getEventIndexRolloverService() {
    return getApplicationContext().getBean(EventIndexRolloverService.class);
  }

  public ExternalProcessVariableIndexRolloverService getExternalProcessVariableIndexRolloverService() {
    return getApplicationContext().getBean(ExternalProcessVariableIndexRolloverService.class);
  }

  public LocalizationService getLocalizationService() {
    return getApplicationContext().getBean(LocalizationService.class);
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return getApplicationContext().getBean(OptimizeElasticsearchClient.class);
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getApplicationContext().getBean(DateTimeFormatter.class);
  }

}
