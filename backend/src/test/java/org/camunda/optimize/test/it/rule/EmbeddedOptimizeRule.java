package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.status.ProgressDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.engine.importing.EngineImportScheduler;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.SynchronizationElasticsearchImportJob;
import org.elasticsearch.client.Client;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);

  /**
   * 1. Reset import start indexes
   *
   * 2. Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   *
   * NOTE: this will not store indexes in the ES.
   */
  public void scheduleAllJobsAndImportEngineEntities() {

    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = getElasticsearchImportJobExecutor();
    elasticsearchImportJobExecutor.startExecutingImportJobs();

    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        logger.debug("scheduling first import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
        // we need another round for the scroll based import index handler
        logger.debug("scheduling second import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }
    resetProcessDefinitionManager();
  }

  /**
   * use this method if you want to rely on backoff and reset implementation provided
   * by optimize itself. It will perform 3 import rounds in total to ensure that:
   *
   * 1. backoff\reset has happened
   * 2. PI are imported
   * 3. Scrolling entities are imported
   *
   * NOTE: you have to adjust backoff and reset times manually in your test before
   * invoking this method
   */
  public void importWithoutReset() {
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = getElasticsearchImportJobExecutor();
    elasticsearchImportJobExecutor.startExecutingImportJobs();

    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        //reset should happen
        scheduleImportAndWaitUntilIsFinished(scheduler);

        //first round is through, update indexes in ES
        logger.debug("scheduling second import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);

        //scroll based imports are through
        logger.debug("scheduling third import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }
  }

  private void scheduleImportAndWaitUntilIsFinished(EngineImportScheduler scheduler) {
    scheduler.scheduleUntilImportIsFinished();
    makeSureAllScheduledJobsAreFinished();
  }

  public void storeImportIndexesToElasticsearch() {
    for (EngineContext engineContext : getApplicationContext().getBean(EngineContextFactory.class).getConfiguredEngines()) {
      StoreIndexesEngineImportMediator storeIndexesEngineImportJobFactory = (StoreIndexesEngineImportMediator)
          getApplicationContext().getBean(
              BeanHelper.getBeanName(StoreIndexesEngineImportMediator.class),
              engineContext
          );
      storeIndexesEngineImportJobFactory.disableBlocking();

      storeIndexesEngineImportJobFactory.importNextPage();

      makeSureAllScheduledJobsAreFinished();
    }

  }

  private void makeSureAllScheduledJobsAreFinished() {

    CountDownLatch synchronizationObject = new CountDownLatch(2);
    SynchronizationElasticsearchImportJob importJob =
      new SynchronizationElasticsearchImportJob(synchronizationObject);
    try {
      getElasticsearchImportJobExecutor().executeImportJob(importJob);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    try {
      synchronizationObject.countDown();
      synchronizationObject.await();
    } catch (InterruptedException e) {
      logger.error("interrupted while synchronizing", e);
    }
  }

  public void scheduleImport() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.scheduleNextRound();
      makeSureAllScheduledJobsAreFinished();
    }
  }

  private EngineImportSchedulerFactory getImportSchedulerFactory() {
    return getOptimize().getApplicationContext().getBean(EngineImportSchedulerFactory.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    return TestEmbeddedCamundaOptimize.getInstance();
  }

  private ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public void initializeSchema() {
    getOptimize().initializeSchema();
  }

  protected void starting(Description description) {
    try {
      startOptimize();
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }
  }

  public String getAuthenticationToken() {
    return getOptimize().getAuthenticationToken();
  }

  public String getAuthorizationHeader() {
    return "Bearer " + getAuthenticationToken();
  }

  public String getNewAuthenticationToken() {
    return getOptimize().getNewAuthenticationToken();
  }

  public String authenticateDemo() {
    Response tokenResponse = authenticateDemoRequest();
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateDemoRequest() {
    return authenticateUserRequest("demo", "demo");
  }

  public Response authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() {
    try {
      getOptimize().start();
      getAlertService().init();
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
  }

  protected void finished(Description description) {
    try {
      this.getAlertService().getScheduler().clear();
    } catch (SchedulerException e) {
      logger.error("cant clear scheduler after test", e);
    }
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    LocalDateUtil.reset();
    reloadConfiguration();
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void stopOptimize() {
    try {
      this.getAlertService().destroy();
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

  public String getProcessDefinitionEndpoint() {
    return getConfigurationService().getProcessDefinitionEndpoint();
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
          Optional<DefinitionBasedImportPage> page = handler.getNextPage();
          page.ifPresent(definitionBasedImportPage ->
            indexes.add(definitionBasedImportPage.getTimestampOfLastEntity().toEpochSecond())
          );
        });
    }

    return indexes;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedImportIndexHandler() {
    List<DefinitionBasedImportIndexHandler> indexes = new LinkedList<>();
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      if (importIndexHandler instanceof DefinitionBasedImportIndexHandler) {
        indexes.add((DefinitionBasedImportIndexHandler) importIndexHandler);
      }
    }
    return indexes;
  }

  public void resetImportStartIndexes() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }

    resetProcessDefinitionManager();
  }

  public void resetProcessDefinitionManager() {
    getApplicationContext().getBean(ProcessDefinitionManager.class).reset();
  }

  public long getProgressValue() {
    return this.target()
        .path("status/import-progress")
        .request()
        .get(ProgressDto.class).getProgress();
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

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    resetProcessDefinitionManager();
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      if (getIndexProvider().getDefinitionBasedHandlers(engineAlias) != null) {
        for (DefinitionBasedImportIndexHandler importIndexHandler : getIndexProvider().getDefinitionBasedHandlers(engineAlias)) {
          importIndexHandler.updateImportIndex();
        }
      }

      if (getIndexProvider().getAllEntitiesBasedHandlers(engineAlias) != null) {
        for (AllEntitiesBasedImportIndexHandler importIndexHandler : getIndexProvider().getAllEntitiesBasedHandlers(engineAlias)) {
          importIndexHandler.updateMaxEntityCount();
        }
      }
    }
  }

  private ImportIndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(ImportIndexHandlerProvider.class);
  }

  public void restartImportCycle() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.restartImportCycle();
    }
  }

  public AlertService getAlertService() {
    return getApplicationContext().getBean(AlertService.class);
  }


  public ElasticSearchSchemaInitializer getSchemaInitializer() {
    return getApplicationContext().getBean(ElasticSearchSchemaInitializer.class);
  }

  public ElasticSearchSchemaManager getElasticSearchSchemaManager() {
    return getApplicationContext().getBean(ElasticSearchSchemaManager.class);
  }

  public Client getTransportClient() {
    return getApplicationContext().getBean(Client.class);
  }

  public String format(OffsetDateTime offsetDateTime) {
    return this.getDateTimeFormatter().format(offsetDateTime);
  }
}
