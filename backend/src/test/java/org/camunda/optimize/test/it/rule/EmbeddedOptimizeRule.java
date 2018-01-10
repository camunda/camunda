package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.engine.importing.EngineImportJobSchedulerFactory;
import org.camunda.optimize.service.engine.importing.EngineImportJobExecutor;
import org.camunda.optimize.service.engine.importing.EngineImportJobScheduler;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.job.factory.StoreIndexesEngineImportJobFactory;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.SynchronizationEngineImportJob;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
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
  public void scheduleAllJobsAndImportEngineEntities() throws InterruptedException {

    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = getElasticsearchImportJobExecutor();
    EngineImportJobExecutor engineImportJobExecutor = getEngineImportJobExecutor();
    engineImportJobExecutor.startExecutingImportJobs();
    elasticsearchImportJobExecutor.startExecutingImportJobs();

    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }

    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        logger.debug("scheduling first import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
        // we need another round for the scroll based import index handler
        logger.debug("scheduling second import round");
        this.waitForBackoff();
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }
  }

  public void waitForBackoff() throws InterruptedException {
    boolean needSleep = true;
    while(needSleep) {

      boolean noSleepingRound = true;
      for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
        long millis = scheduler.maxTimeToSleep();
        if (millis > 0 ) {
          logger.debug("sleeping for [{}]ms", millis);
          Thread.sleep(millis);
          noSleepingRound = false;
        }
      }

      if (noSleepingRound) {
        needSleep = false;
      }
    }

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
   *
   * @throws InterruptedException
   */
  public void importWithoutReset() throws InterruptedException {
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = getElasticsearchImportJobExecutor();
    EngineImportJobExecutor engineImportJobExecutor = getEngineImportJobExecutor();
    engineImportJobExecutor.startExecutingImportJobs();
    elasticsearchImportJobExecutor.startExecutingImportJobs();

    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        //reset should happen
        this.waitForBackoff();
        scheduleImportAndWaitUntilIsFinished(scheduler);

        //first round is through, update indexes in ES
        logger.debug("scheduling second import round");
        this.waitForBackoff();
        scheduleImportAndWaitUntilIsFinished(scheduler);

        //scroll based imports are through
        logger.debug("scheduling third import round");
        this.waitForBackoff();
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }
  }

  private void scheduleImportAndWaitUntilIsFinished(EngineImportJobScheduler scheduler) {
    scheduler.scheduleUntilCantCreateNewJobs();
    makeSureAllScheduledJobsAreFinished();
  }

  public void storeImportIndexesToElasticsearch() {
    for (EngineContext engineContext : getApplicationContext().getBean(EngineContextFactory.class).getConfiguredEngines()) {
      StoreIndexesEngineImportJobFactory storeIndexesEngineImportJobFactory = (StoreIndexesEngineImportJobFactory)
          getApplicationContext().getBean(
              BeanHelper.getBeanName(StoreIndexesEngineImportJobFactory.class),
              engineContext
          );
      storeIndexesEngineImportJobFactory.disableBlocking();

      Runnable storeIndexesEngineImportJob =
          storeIndexesEngineImportJobFactory.getNextJob().get();

      try {
        getEngineImportJobExecutor().executeImportJob(storeIndexesEngineImportJob);
      } catch (InterruptedException e) {
        logger.error("interrupted while persisting data", e);
      }

      makeSureAllScheduledJobsAreFinished();
    }

  }

  private void makeSureAllScheduledJobsAreFinished() {
    CountDownLatch synchronizationObject = new CountDownLatch(2);
    SynchronizationEngineImportJob synchronizationEngineImportJob =
      new SynchronizationEngineImportJob(getElasticsearchImportJobExecutor(), synchronizationObject);

    try {
      getEngineImportJobExecutor().executeImportJob(synchronizationEngineImportJob);
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
    for (EngineImportJobScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.scheduleNextRound();
    }
  }

  private EngineImportJobSchedulerFactory getImportSchedulerFactory() {
    return getOptimize().getApplicationContext().getBean(EngineImportJobSchedulerFactory.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    return TestEmbeddedCamundaOptimize.getInstance();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public void initializeSchema() {
    getOptimize().initializeSchema();
  }

  public EngineImportJobExecutor getEngineImportJobExecutor() {
    return getOptimize().getEngineImportJobExecutor();
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
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
  }

  protected void finished(Description description) {
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    reloadConfiguration();
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void stopOptimize() {
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
          .forEach(handler -> indexes.add(handler.getCurrentDefinitionBasedImportIndex()));
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

  public ImportIndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(ImportIndexHandlerProvider.class);
  }

  public void restartImportCycle() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.restartImportCycle();
    }
  }
}
