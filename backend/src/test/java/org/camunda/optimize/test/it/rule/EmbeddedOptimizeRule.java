package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.util.ConfigurationService;
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

  private TestEmbeddedCamundaOptimize camundaOptimize;

  /**
   * Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   */
  public void scheduleAllJobsAndImportEngineEntities() throws OptimizeException {
    ImportScheduler scheduler = scheduleImport();
    getJobExecutor().startExecutingImportJobs();

    while (scheduler.hasStillJobsToExecute()) {
      ImportScheduleJob nextToExecute = scheduler.getNextToExecute();
      ImportResult result = nextToExecute.execute();
      scheduler.postProcess(nextToExecute, result);
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

    if (scheduler.hasStillJobsToExecute()) {
      ImportScheduleJob nextToExecute = scheduler.getNextToExecute();
      ImportResult result = nextToExecute.execute();
      scheduler.postProcess(nextToExecute, result);
    }

    getJobExecutor().stopExecutingImportJobs();
  }

  public ImportScheduler scheduleImport() {
    ImportScheduler scheduler = getImportScheduler();
    scheduler.scheduleNewImportRound();
    return scheduler;
  }

  private ImportScheduler getImportScheduler() {
    return camundaOptimize.getApplicationContext().getBean(ImportScheduler.class);
  }

  private ScheduleJobFactory getScheduleFactory() {
    return camundaOptimize.getImportScheduleFactory();
  }

  private ImportServiceProvider getServiceProvider() {
    return camundaOptimize.getImportServiceProvider();
  }

  private ImportJobExecutor getJobExecutor() {
    return camundaOptimize.getImportJobExecutor();
  }

  protected void starting(Description description) {
    startOptimize();
    resetImportStartIndexes();
  }

  public String getAuthenticationToken() {
    return camundaOptimize.getAuthenticationToken();
  }

  public String authenticateDemo() {
    Response tokenResponse = authenticateDemoRequest();
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateDemoRequest() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("demo");
    entity.setPassword("demo");

    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() {
    camundaOptimize = TestEmbeddedCamundaOptimize.getInstance();
    try {
      camundaOptimize.start();
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
  }

  public void initializeSchema() {
    camundaOptimize.initializeSchema();
  }

  protected void finished(Description description) {
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    reloadConfiguration();
    getImportScheduler().clearQueue();
  }

  public void reloadConfiguration() {
    camundaOptimize.reloadConfiguration();
  }

  public void stopOptimize() {
    try {
      camundaOptimize.destroy();
    } catch (Exception e) {
      logger.error("Failed to stop Optimize", e);
    }
  }

  public final WebTarget target(String path) {
    return camundaOptimize.target(path);
  }

  public final WebTarget target() {
    return camundaOptimize.target();
  }

  public final WebTarget rootTarget(String path) {
    return camundaOptimize.rootTarget(path);
  }

  public final WebTarget rootTarget() {
    return camundaOptimize.rootTarget();
  }

  public String getProcessDefinitionEndpoint() {
    return getConfigurationService().getProcessDefinitionEndpoint();
  }

  public List<Integer> getImportIndexes() {
    List<Integer> indexes = new LinkedList<>();
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      indexes.add(importService.getImportStartIndex());
    }
    return indexes;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedImportIndexHandler() {
    List<DefinitionBasedImportIndexHandler> indexes = new LinkedList<>();
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      if (importService.getImportIndexHandler() instanceof DefinitionBasedImportIndexHandler) {
        indexes.add((DefinitionBasedImportIndexHandler)importService.getImportIndexHandler());
      }
    }
    return indexes;
  }

  public void restartImportCycle() {
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      importService.restartImportCycle();
    }
  }

  public void resetImportStartIndexes() {
    getJobExecutor().startExecutingImportJobs();
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      importService.resetImportStartIndex();
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
    camundaOptimize.startImportScheduler();
  }

  public boolean isImporting() {
    return this.getJobExecutor().isActive();
  }

  public ApplicationContext getApplicationContext() {
    return camundaOptimize.getApplicationContext();
  }


  public int getMaxVariableValueListSize() {
    return getConfigurationService().getMaxVariableValueListSize();
  }

  public ConfigurationService getConfigurationService() {
    return camundaOptimize.getConfigurationService();
  }

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      importService.updateImportIndex();
    }
  }
}
