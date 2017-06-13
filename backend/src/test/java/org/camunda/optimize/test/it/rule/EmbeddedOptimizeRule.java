package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
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

  public void importEngineEntities() throws OptimizeException {
    getJobExecutor().startExecutingImportJobs();
    for(ImportScheduleJob job : getScheduleFactory().createPagedJobs()) {
      ImportResult result = job.execute();
      for (ImportScheduleJob idJob : getScheduleFactory().createIndexedScheduleJobs(result.getIdsToFetch())) {
        idJob.execute();
      }
    }
    getJobExecutor().stopExecutingImportJobs();
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

  protected void finished(Description description) {
    TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
    reloadConfiguration();
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

  public void updateImportIndexes() {
    for (PaginatedImportService importService : getServiceProvider().getPagedServices()) {
      importService.updateImportIndex();
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
}
