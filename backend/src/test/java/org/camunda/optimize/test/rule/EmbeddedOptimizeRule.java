package org.camunda.optimize.test.rule;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportScheduleJob;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Properties;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 *
 * @author Askar Akhmerov
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);

  private EmbeddedCamundaOptimize camundaOptimize;
  private Properties properties;

  public void init() {
    properties = PropertyUtil.loadProperties("it-test.properties");
  }

  public void importEngineEntities() {
    getJobExecutor().startExecutingImportJobs();
    for (ImportService importService : getServiceProvider().getServices()) {
      ImportScheduleJob job = new ImportScheduleJob();
      job.setImportService(importService);
      job.execute();
    }
    getJobExecutor().stopExecutingImportJobs();
  }

  private ImportServiceProvider getServiceProvider() {
    return camundaOptimize.getImportServiceProvider();
  }

  private ImportJobExecutor getJobExecutor() {
    return camundaOptimize.getImportJobExecutor();
  }

  protected void starting(Description description) {
    if (camundaOptimize == null) {
      camundaOptimize = new EmbeddedCamundaOptimize("classpath:embeddedOptimizeContext.xml");
      init();
    }
    try {
      camundaOptimize.start();
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
  }

  protected void finished(Description description) {
    try {
      camundaOptimize.destroy();
      camundaOptimize = null;
    } catch (Exception e) {
      logger.error("Failed to stop Optimize", e);
    }
  }

  public String authenticateAdmin() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    Response tokenResponse =  target("authentication")
        .request()
        .post(Entity.json(entity));

    return tokenResponse.readEntity(String.class);
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
    return ClientBuilder.newClient();
  }

  public String getProcessDefinitionEndpoint() {
    return properties.getProperty("camunda.optimize.test.embedded-optimize.process-definition");
  }

  public void resetImportStartIndex() {
    for (ImportService importService : getServiceProvider().getServices()) {
      importService.resetImportStartIndex();
    }
  }

  public void initializeSchema() {
    camundaOptimize.initializeIndex();
  }

  public int getProgressValue() {
    return this.target()
        .path("status/import-progress")
        .request()
        .get(CountDto.class).getCount();
  }

  public void startImportScheduler() {
    camundaOptimize.startImportScheduler();
  }

  public boolean isImporting() {
    return this.getJobExecutor().isActive();
  }
}
