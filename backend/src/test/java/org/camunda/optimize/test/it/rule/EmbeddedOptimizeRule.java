package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);

  /**
   * Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   */
  public void scheduleAllJobsAndImportEngineEntities() throws OptimizeException {
    ImportScheduler scheduler = scheduleImport();
    getJobExecutor().startExecutingImportJobs();

    while (scheduler.hasStillJobsToExecute()) {
      executeIfJobsArePresent(scheduler);
    }

    getJobExecutor().stopExecutingImportJobs();
  }

  /**
   * Execute one round\job using import scheduler infrastructure
   *
   * NOTE: this method does not invoke scheduling of jobs
   */
  public void importEngineEntitiesRound() throws OptimizeException {
    getJobExecutor().startExecutingImportJobs();
    ImportScheduler scheduler = getImportScheduler();

    executeIfJobsArePresent(scheduler);

    getJobExecutor().stopExecutingImportJobs();
  }

  private void executeIfJobsArePresent(ImportScheduler scheduler) throws OptimizeException {
    if (scheduler.hasStillJobsToExecute()) {
      ImportScheduleJob nextToExecute = scheduler.getNextToExecute();
      ImportResult result = getServiceProvider().getImportService(nextToExecute.getElasticsearchType()).executeImport(nextToExecute);
      result.setEngineHasStillNewData(scheduler.handleIndexes(result, nextToExecute));
      scheduler.postProcess(nextToExecute, result);
    }
  }

  public ImportScheduler scheduleImport() {
    ImportScheduler scheduler = getImportScheduler();
    scheduler.scheduleNewImportRound();
    return scheduler;
  }

  private ImportScheduler getImportScheduler() {
    return getOptimize().getApplicationContext().getBean(ImportScheduler.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    return TestEmbeddedCamundaOptimize.getInstance();
  }

  private ScheduleJobFactory getScheduleFactory() {
    return getOptimize().getImportScheduleFactory();
  }

  private ImportServiceProvider getServiceProvider() {
    return getOptimize().getImportServiceProvider();
  }

  public ImportJobExecutor getJobExecutor() {
    return getOptimize().getImportJobExecutor();
  }

  protected void starting(Description description) {
    startOptimize();
    resetImportStartIndexes();
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

  public void initializeSchema() {
    getOptimize().initializeSchema();
  }

  protected void finished(Description description) {
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    reloadConfiguration();
    getImportScheduler().clearQueue();
    getIndexProvider().unregisterHandlers();
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

  public List<Integer> getImportIndexes() {
    List<Integer> indexes = new LinkedList<>();
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      indexes.add(importIndexHandler.getAbsoluteImportIndex());
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

  public void restartImportCycle() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.restartImportCycle();
    }
  }

  public void resetImportStartIndexes() {
    getJobExecutor().startExecutingImportJobs();
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }
    getJobExecutor().stopExecutingImportJobs();
  }

  public int getProgressValue() {
    return this.target()
        .path("status/import-progress")
        .request()
        .get(ProgressDto.class).getProgress();
  }

  public void startImportScheduler() {
    getOptimize().startImportScheduler();
  }

  public boolean isImporting() {
    return this.getJobExecutor().isActive();
  }

  public ApplicationContext getApplicationContext() {
    return getOptimize().getApplicationContext();
  }


  public int getMaxVariableValueListSize() {
    return getConfigurationService().getMaxVariableValueListSize();
  }

  public ConfigurationService getConfigurationService() {
    return getOptimize().getConfigurationService();
  }

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.updateImportIndex();
    }
  }

  private IndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(IndexHandlerProvider.class);
  }
}
