/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.cleanup.OptimizeCleanupScheduler;
import org.camunda.optimize.service.engine.importing.EngineImportScheduler;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.camunda.optimize.test.util.SynchronizationElasticsearchImportJob;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  public static final String DEFAULT_ENGINE_ALIAS = "1";

  private String context = null;
  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);
  private OptimizeRequestExecutor requestExecutor;
  private ObjectMapper objectMapper;

  private boolean resetImportOnStart = true;

  /**
   * 1. Reset import start indexes
   * <p>
   * 2. Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   * <p>
   * NOTE: this will not store indexes in the ES.
   */
  public EmbeddedOptimizeRule() {
  }

  public EmbeddedOptimizeRule(String context) {
    this.context = context;
  }

  public void startContinuousImportScheduling() {
    getOptimize().startImportSchedulers();
  }

  public void importAllEngineData() {
    boolean importInProgress = true;
    while (importInProgress) {
      boolean currentImportInProgress = false;
      for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
        scheduler.scheduleNextRound();
        currentImportInProgress |= scheduler.isImporting();
      }
      importInProgress = currentImportInProgress;
    }
    makeSureAllScheduledJobsAreFinished();
  }

  public void importAllEngineEntitiesFromScratch() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }
    importAllEngineEntitiesFromLastIndex();
  }

  public void importAllEngineEntitiesFromLastIndex() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        logger.debug("scheduling import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }

  }

  public void importRunningActivityInstance(List<HistoricActivityInstanceEngineDto> activities) {
    RunningActivityInstanceWriter writer = getApplicationContext().getBean(RunningActivityInstanceWriter.class);

    for (EngineContext configuredEngine : getConfiguredEngines()) {
      RunningActivityInstanceImportService service =
        new RunningActivityInstanceImportService(writer, getElasticsearchImportJobExecutor(), configuredEngine);
      service.executeImport(activities, () -> {
      });
    }
    makeSureAllScheduledJobsAreFinished();
  }

  private void resetImportBackoff() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler
        .getImportMediators()
        .forEach(EngineImportMediator::resetBackoff);
    }
  }

  private void scheduleImportAndWaitUntilIsFinished(EngineImportScheduler scheduler) {
    resetImportBackoff();
    scheduler.scheduleNextRound();
    makeSureAllScheduledJobsAreFinished();
    scheduler.scheduleNextRoundScrollBasedOnly();
    makeSureAllScheduledJobsAreFinished();
  }

  public void storeImportIndexesToElasticsearch() {
    for (EngineContext engineContext : getConfiguredEngines()) {
      StoreIndexesEngineImportMediator storeIndexesEngineImportJobFactory =
        getApplicationContext().getBean(
          StoreIndexesEngineImportMediator.class,
          engineContext
        );
      storeIndexesEngineImportJobFactory.disableBlocking();

      storeIndexesEngineImportJobFactory.importNextPage();

      makeSureAllScheduledJobsAreFinished();
    }

  }

  private List<EngineContext> getConfiguredEngines() {
    return getApplicationContext().getBean(EngineContextFactory.class).getConfiguredEngines();
  }

  public EngineConfiguration getDefaultEngineConfiguration() {
    return getConfigurationService()
      .getEngineConfiguration(DEFAULT_ENGINE_ALIAS)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Missing default engine configuration"));
  }

  public void makeSureAllScheduledJobsAreFinished() {
    final List<CompletableFuture<Void>> synchronizationCompletables = new ArrayList<>();
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.getImportMediators()
        .stream()
        .map(EngineImportMediator::getImportJobExecutor)
        .forEach(importJobExecutor -> {
          final CompletableFuture<Void> toComplete = new CompletableFuture<>();
          synchronizationCompletables.add(toComplete);
          importJobExecutor.executeImportJob(new SynchronizationElasticsearchImportJob(toComplete));
        });
    }

    CompletableFuture.allOf(synchronizationCompletables.toArray(new CompletableFuture[0])).join();
  }

  public EngineImportSchedulerFactory getImportSchedulerFactory() {
    return getOptimize().getApplicationContext().getBean(EngineImportSchedulerFactory.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    if (context != null) {
      return TestEmbeddedCamundaOptimize.getInstance(context);
    } else {
      return TestEmbeddedCamundaOptimize.getInstance();
    }
  }

  private ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutor;
  }

  protected void starting(Description description) {
    try {
      startOptimize();
      objectMapper = getApplicationContext().getBean(ObjectMapper.class);
      requestExecutor =
        new OptimizeRequestExecutor(
          getOptimize().target(),
          getAuthorizationCookieValue(),
          objectMapper
        );
      if (isResetImportOnStart()) {
        resetImportStartIndexes();
      }
    } catch (Exception e) {
      //nothing to do here
    }
  }

  public String getAuthenticationToken() {
    return getOptimize().getAuthenticationToken();
  }

  private String getAuthorizationCookieValue() {
    return AuthCookieService.createOptimizeAuthCookieValue(getAuthenticationToken());
  }

  public String getNewAuthenticationToken() {
    return getOptimize().getNewAuthenticationToken();
  }

  public String authenticateUser(String username, String password) {
    Response tokenResponse = authenticateUserRequest(username, password);
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() throws Exception {
    getOptimize().start();
    getAlertService().init();
    getElasticsearchImportJobExecutor().startExecutingImportJobs();
  }

  protected void finished(Description description) {
    try {
      this.getAlertService().getScheduler().clear();
      invalidateAuthorizationCache();
      TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
      LocalDateUtil.reset();
      reloadConfiguration();
    } catch (Exception e) {
      logger.error("Failed to clean up after test", e);
    }
  }

  private void invalidateAuthorizationCache()
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Method invalidateCacheMethod = DefinitionAuthorizationService.class.getDeclaredMethod("reset");
    invalidateCacheMethod.setAccessible(true);
    invalidateCacheMethod.invoke(getDefinitionAuthorizationService());
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void stopOptimize() {
    try {
      this.getElasticsearchImportJobExecutor().stopExecutingImportJobs();
    } catch (Exception e) {
      logger.error("Failed to stop elasticsearch import", e);
    }

    try {
      this.getAlertService().destroy();
    } catch (Exception e) {
      logger.error("Failed to destroy alert service", e);
    }

    try {
      getOptimize().destroy();
    } catch (Exception e) {
      logger.error("Failed to stop Optimize", e);
    }
  }

  public final WebTarget target(String path) {
    return getOptimize().target(path);
  }

  public final WebTarget target() {
    return getOptimize().target();
  }

  public final WebTarget rootTarget(String path) {
    return getOptimize().rootTarget(path);
  }

  public final WebTarget rootTarget() {
    return getOptimize().rootTarget();
  }

  public List<Long> getImportIndexes() {
    List<Long> indexes = new LinkedList<>();

    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      getIndexProvider()
        .getAllEntitiesBasedHandlers(engineAlias)
        .forEach(handler -> indexes.add(handler.getImportIndex()));
      getIndexProvider()
        .getDefinitionBasedHandlers(engineAlias)
        .forEach(handler -> {
          TimestampBasedImportPage page = handler.getNextPage();
          indexes.add(page.getTimestampOfLastEntity().toEpochSecond());
        });
    }

    return indexes;
  }

  public void resetImportStartIndexes() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }
  }

  public boolean isImporting() {
    return this.getElasticsearchImportJobExecutor().isActive();
  }

  public ApplicationContext getApplicationContext() {
    return getOptimize().getApplicationContext();
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getOptimize().getDateTimeFormatter();
  }

  public ConfigurationService getConfigurationService() {
    return getOptimize().getConfigurationService();
  }

  public OptimizeCleanupScheduler getCleanupScheduler() {
    return getOptimize().getCleanupService();
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      if (getIndexProvider().getDefinitionBasedHandlers(engineAlias) != null) {
        for (TimestampBasedImportIndexHandler importIndexHandler : getIndexProvider().getDefinitionBasedHandlers(
          engineAlias)) {
          importIndexHandler.updateImportIndex();
        }
      }
    }
  }

  public ImportIndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(ImportIndexHandlerProvider.class);
  }

  public AlertService getAlertService() {
    return getApplicationContext().getBean(AlertService.class);
  }

  public DefinitionAuthorizationService getDefinitionAuthorizationService() {
    return getApplicationContext().getBean(DefinitionAuthorizationService.class);
  }

  public TenantService getTenantService() {
    return getApplicationContext().getBean(TenantService.class);
  }

  public ElasticSearchSchemaManager getElasticSearchSchemaManager() {
    return getApplicationContext().getBean(ElasticSearchSchemaManager.class);
  }

  public RestHighLevelClient getElasticsearchClient() {
    return getApplicationContext().getBean(RestHighLevelClient.class);
  }

  public String format(OffsetDateTime offsetDateTime) {
    return this.getDateTimeFormatter().format(offsetDateTime);
  }

  public String formatToHistogramBucketKey(final OffsetDateTime offsetDateTime, final ChronoUnit unit) {
    return getDateTimeFormatter().format(truncateToStartOfUnit(offsetDateTime, unit));
  }

  public boolean isResetImportOnStart() {
    return resetImportOnStart;
  }

  public void setResetImportOnStart(final boolean resetImportOnStart) {
    this.resetImportOnStart = resetImportOnStart;
  }
}
