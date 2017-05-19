package org.camunda.optimize.test.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.ProgressDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);

  private TestEmbeddedCamundaOptimize camundaOptimize;
  private Properties properties;
  private final static String propertiesLocation = "it/it-test.properties";

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

  public void startOptimize() {
    camundaOptimize = TestEmbeddedCamundaOptimize.getInstance();
    properties = PropertyUtil.loadProperties(propertiesLocation);
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

  public String authenticateAdmin() {
    Response tokenResponse = authenticateAdminRequest();

    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateAdminRequest() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    return target("authentication")
        .request()
        .post(Entity.json(entity));
  }

  public final WebTarget target(String path) {
    return this.target().path(path);
  }

  public final WebTarget target() {
    return this.client().target(getBaseUri());
  }

  private String getBaseUri() {
    return properties.getProperty("camunda.optimize.test.embedded-optimize");
  }

  public final Client client() {
    return this.getClient();
  }

  private Client getClient() {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, 10000);
    client.property(ClientProperties.READ_TIMEOUT,    10000);
    return client;
  }

  public String getProcessDefinitionEndpoint() {
    return properties.getProperty("camunda.optimize.test.embedded-optimize.process-definition");
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

  public void initializeSchema() {
    camundaOptimize.initializeIndex();
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
