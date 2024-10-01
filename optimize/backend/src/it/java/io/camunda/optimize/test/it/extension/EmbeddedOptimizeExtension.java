/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.optimize.ApplicationContextProvider;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.KpiEvaluationSchedulerService;
import io.camunda.optimize.service.KpiService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.alert.AlertService;
import io.camunda.optimize.service.cleanup.CleanupScheduler;
import io.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import io.camunda.optimize.service.dashboard.ManagementDashboardService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.digest.DigestService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.AbstractImportScheduler;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportSchedulerManagerService;
import io.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import io.camunda.optimize.service.importing.ingested.IngestedDataImportScheduler;
import io.camunda.optimize.service.importing.ingested.mediator.StoreIngestedImportProgressMediator;
import io.camunda.optimize.service.importing.zeebe.mediator.StorePositionBasedImportProgressMediator;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class EmbeddedOptimizeExtension
    implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

  public static final String DEFAULT_ENGINE_ALIAS = "camunda-bpm";
  private static final ObjectMapper configObjectMapper =
      new ObjectMapper().registerModules(new JavaTimeModule(), new Jdk8Module());
  private static String serializedDefaultConfiguration;
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EmbeddedOptimizeExtension.class);
  private final boolean beforeAllMode;
  private ApplicationContext applicationContext;
  private OptimizeRequestExecutor requestExecutor;
  private ObjectMapper objectMapper;
  private boolean resetImportOnStart = true;
  private boolean closeContextAfterTest = false;

  public EmbeddedOptimizeExtension() {
    this(false);
  }

  public EmbeddedOptimizeExtension(final boolean beforeAllMode) {
    log.info("Running tests with database {}", IntegrationTestConfigurationUtil.getDatabaseType());
    System.setProperty(
        CAMUNDA_OPTIMIZE_DATABASE, IntegrationTestConfigurationUtil.getDatabaseType().getId());
    this.beforeAllMode = beforeAllMode;
  }

  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    setApplicationContext(SpringExtension.getApplicationContext(extensionContext));
    if (serializedDefaultConfiguration == null) {
      // store the default configuration to restore it later
      try {
        serializedDefaultConfiguration =
            configObjectMapper.writeValueAsString(getConfigurationService());
      } catch (final JsonProcessingException e) {
        throw new OptimizeRuntimeException(e);
      }
    }
    if (beforeAllMode) {
      setupOptimize();
    }
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    setApplicationContext(SpringExtension.getApplicationContext(extensionContext));
    initMetadataIfMissing();
    if (!beforeAllMode) {
      setupOptimize();
    }
  }

  @Override
  public void afterAll(final ExtensionContext extensionContext) {
    if (beforeAllMode) {
      afterTest();
    }
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    if (!beforeAllMode) {
      afterTest();
    }
    if (closeContextAfterTest) {
      ((ConfigurableApplicationContext) applicationContext).close();
      setCloseContextAfterTest(false);
      setApplicationContext(SpringExtension.getApplicationContext(extensionContext));
      reloadConfiguration();
    }
  }

  public void setupOptimize() {
    try {
      objectMapper = getBean(ObjectMapper.class);
      requestExecutor =
          new OptimizeRequestExecutor(
                  DEFAULT_USERNAME,
                  DEFAULT_PASSWORD,
                  IntegrationTestConfigurationUtil.getEmbeddedOptimizeRestApiEndpoint(
                      applicationContext))
              .initAuthCookie();
      if (isResetImportOnStart()) {
        resetPositionBasedImportStartIndexes();
        setResetImportOnStart(true);
      }
    } catch (final Exception e) {
      final String message = "Failed starting embedded Optimize.";
      log.error(message, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

  public void afterTest() {
    try {
      getAlertService().getScheduler().clear();
      stopImportScheduling();
      resetConfiguration();
      LocalDateUtil.reset();
      reloadConfiguration();
    } catch (final Exception e) {
      log.error("Failed to clean up after test", e);
    }
  }

  public void configureDbHostAndPort(final String host, final int dbPort) {
    getConfigurationService()
        .getElasticSearchConfiguration()
        .getConnectionNodes()
        .get(0)
        .setHost(host);
    getConfigurationService()
        .getElasticSearchConfiguration()
        .getConnectionNodes()
        .get(0)
        .setHttpPort(dbPort);
    getConfigurationService()
        .getOpenSearchConfiguration()
        .getConnectionNodes()
        .get(0)
        .setHost(host);
    getConfigurationService()
        .getOpenSearchConfiguration()
        .getConnectionNodes()
        .get(0)
        .setHttpPort(dbPort);
    reloadConfiguration();
  }

  public void configureEngineRestEndpointForEngineWithName(
      final String engineName, final String restEndpoint) {
    getConfigurationService().getConfiguredEngines().values().stream()
        .filter(config -> config.getName().equals(engineName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException("Cannot find configured engine with name " + engineName))
        .setRest(restEndpoint);
  }

  public void startContinuousImportScheduling() {
    getImportSchedulerManager().startSchedulers();
  }

  public void stopImportScheduling() {
    getImportSchedulerManager().stopSchedulers();
  }

  public void importIngestedDataFromScratch() {
    try {
      resetPositionBasedImportStartIndexes();
    } catch (final Exception e) {
      // nothing to do
    }
    final IngestedDataImportScheduler scheduler =
        getImportSchedulerManager().getIngestedDataImportScheduler().orElseThrow();
    try {
      scheduler.runImportRound(true).get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public void importIngestedDataFromLastIndex() {
    final IngestedDataImportScheduler scheduler =
        getImportSchedulerManager().getIngestedDataImportScheduler().orElseThrow();
    try {
      scheduler.runImportRound(true).get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public void importAllZeebeEntitiesFromScratch() {
    try {
      resetPositionBasedImportStartIndexes();
    } catch (final Exception e) {
      // nothing to do
    }
    importAllZeebeEntitiesFromLastIndex();
  }

  public void importAllZeebeEntitiesFromLastIndex() {
    try {
      getImportSchedulerManager()
          .getZeebeImportScheduler()
          .orElseThrow(() -> new OptimizeIntegrationTestException("No Zeebe Scheduler present"))
          .runImportRound(true)
          .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public void storeImportIndexesToElasticsearch() {
    final List<CompletableFuture<Void>> synchronizationCompletables = new ArrayList<>();
    final List<AbstractImportScheduler<?>> importSchedulers =
        getImportSchedulerManager().getImportSchedulers().stream()
            .filter(
                scheduler ->
                    getConfigurationService().isImportEnabled(scheduler.getDataImportSourceDto()))
            .collect(Collectors.toList());
    for (final AbstractImportScheduler<?> scheduler : importSchedulers) {
      synchronizationCompletables.addAll(
          scheduler.getImportMediators().stream()
              .filter(
                  med ->
                      med instanceof StoreIngestedImportProgressMediator
                          || med instanceof StorePositionBasedImportProgressMediator)
              .map(
                  mediator -> {
                    mediator.resetBackoff();
                    return mediator.runImport();
                  })
              .toList());
    }
    CompletableFuture.allOf(synchronizationCompletables.toArray(new CompletableFuture[0])).join();
  }

  public EngineConfiguration getDefaultEngineConfiguration() {
    return getConfigurationService()
        .getEngineConfiguration(DEFAULT_ENGINE_ALIAS)
        .orElseThrow(
            () -> new OptimizeIntegrationTestException("Missing default engine configuration"));
  }

  public ImportSchedulerManagerService getImportSchedulerManager() {
    return getBean(ImportSchedulerManagerService.class);
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutor;
  }

  public String getNewAuthenticationToken() {
    return authenticateUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String authenticateUser(final String username, final String password) {
    final Response tokenResponse = authenticateUserRequest(username, password);
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateUserRequest(final String username, final String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    return target("authentication").request().post(Entity.json(entity));
  }

  public void initMetadataIfMissing() {
    getDatabaseMetadataService().initMetadataIfMissing(getOptimizeDatabaseClient());
  }

  public void reloadConfiguration() {
    final Map<String, ?> refreshableServices =
        getApplicationContext().getBeansOfType(ConfigurationReloadable.class);
    for (final Map.Entry<String, ?> entry : refreshableServices.entrySet()) {
      final Object beanRef = entry.getValue();
      if (beanRef instanceof ConfigurationReloadable) {
        final ConfigurationReloadable reloadable = (ConfigurationReloadable) beanRef;
        reloadable.reloadConfiguration(getApplicationContext());
      }
    }
    getOptimizeDatabaseClient().setDefaultRequestOptions();
  }

  public void resetConfiguration() throws IOException {
    log.info("resetting config, parsing defaultconfig and copying properties");
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(
        configObjectMapper.readValue(serializedDefaultConfiguration, ConfigurationService.class),
        getBean(ConfigurationService.class));
    log.info("done resetting config");
  }

  public final WebTarget target(final String path) {
    return requestExecutor.getDefaultWebTarget().path(path);
  }

  public final WebTarget target() {
    return requestExecutor.getDefaultWebTarget();
  }

  public final WebTarget rootTarget(final String path) {
    return requestExecutor
        .createWebTarget(
            IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint(applicationContext))
        .path(path);
  }

  public final WebTarget securedRootTarget() {
    return requestExecutor.createWebTarget(
        IntegrationTestConfigurationUtil.getSecuredEmbeddedOptimizeEndpoint(applicationContext));
  }

  public void resetPositionBasedImportStartIndexes() {
    getAllPositionBasedImportHandlers().forEach(PositionBasedImportIndexHandler::resetImportIndex);
  }

  public List<PositionBasedImportIndexHandler> getAllPositionBasedImportHandlers() {
    final List<PositionBasedImportIndexHandler> positionBasedHandlers = new LinkedList<>();
    for (int partitionId = 1;
        partitionId <= getConfigurationService().getConfiguredZeebe().getPartitionCount();
        partitionId++) {
      positionBasedHandlers.addAll(getIndexHandlerRegistry().getPositionBasedHandlers(partitionId));
    }
    return positionBasedHandlers;
  }

  public void reinitializeSchema() {
    getDatabaseSchemaManager().initializeSchema(getOptimizeDatabaseClient());
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public void setApplicationContext(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    applicationContext
        .getBean(ApplicationContextProvider.class)
        .setApplicationContext(applicationContext);
  }

  public <T> T getBean(final Class<T> clazz) {
    return applicationContext.getBean(clazz);
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getBean(DateTimeFormatter.class);
  }

  public ConfigurationService getConfigurationService() {
    return getBean(ConfigurationService.class);
  }

  public ManagementDashboardService getManagementDashboardService() {
    return getApplicationContext().getBean(ManagementDashboardService.class);
  }

  public InstantPreviewDashboardService getInstantPreviewDashboardService() {
    return getApplicationContext().getBean(InstantPreviewDashboardService.class);
  }

  public InstantDashboardMetadataWriter getInstantPreviewDashboardWriter() {
    return getApplicationContext().getBean(InstantDashboardMetadataWriter.class);
  }

  public CleanupScheduler getCleanupScheduler() {
    return getBean(CleanupScheduler.class);
  }

  public KpiEvaluationSchedulerService getKpiSchedulerService() {
    return getBean(KpiEvaluationSchedulerService.class);
  }

  public LocalizationService getLocalizationService() {
    return getBean(LocalizationService.class);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public ImportIndexHandlerRegistry getIndexHandlerRegistry() {
    return getBean(ImportIndexHandlerRegistry.class);
  }

  public AlertService getAlertService() {
    return getBean(AlertService.class);
  }

  public DigestService getDigestService() {
    return getBean(DigestService.class);
  }

  public KpiService getKpiService() {
    return getApplicationContext().getBean(KpiService.class);
  }

  public SettingsService getSettingsService() {
    return getBean(SettingsService.class);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getBean(OptimizeIndexNameService.class);
  }

  public DatabaseSchemaManager getDatabaseSchemaManager() {
    return getBean(DatabaseSchemaManager.class);
  }

  public DatabaseClient getOptimizeDatabaseClient() {
    return getBean(DatabaseClient.class);
  }

  public DatabaseMetadataService getDatabaseMetadataService() {
    return getBean(DatabaseMetadataService.class);
  }

  private boolean isResetImportOnStart() {
    return resetImportOnStart;
  }

  public void setResetImportOnStart(final boolean resetImportOnStart) {
    this.resetImportOnStart = resetImportOnStart;
  }

  public String format(final OffsetDateTime offsetDateTime) {
    return getDateTimeFormatter().format(offsetDateTime);
  }

  public String formatToHistogramBucketKey(
      final OffsetDateTime offsetDateTime, final ChronoUnit unit) {
    return getDateTimeFormatter().format(truncateToStartOfUnit(offsetDateTime, unit));
  }

  public String toJsonString(final Object object) {
    try {
      return getObjectMapper().writeValueAsString(object);
    } catch (final JsonProcessingException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public boolean isCloseContextAfterTest() {
    return closeContextAfterTest;
  }

  public void setCloseContextAfterTest(final boolean closeContextAfterTest) {
    this.closeContextAfterTest = closeContextAfterTest;
  }
}
