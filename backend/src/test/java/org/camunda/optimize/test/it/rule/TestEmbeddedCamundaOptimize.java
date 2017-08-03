package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.util.ConfigurationReloadable;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Properties;

/**
 * This class is wrapper around the embedded optimize to ensure
 * only one instance is used for all tests. Also makes sure the
 * configuration is reset after each test.
 *
 * @author Askar Akhmerov
 */
public class TestEmbeddedCamundaOptimize extends EmbeddedCamundaOptimize {

  private final static String contextLocation = "classpath:embeddedOptimizeContext.xml";
  private final static String propertiesLocation = "it/it-test.properties";

  private static String authenticationToken;
  private Properties properties;

  private static TestEmbeddedCamundaOptimize optimize;

  private static TestEmbeddedCamundaOptimize testOptimizeInstance;

  /**
   * This configuration is stored the first time optimize is started
   * and restored before each test, so you can adapt the test
   * to your custom configuration.
   */
  private static ConfigurationService defaultConfiguration;

  /**
   * This configuration keeps track which settings were changed
   * even if optimize is destroyed during the test.
   */
  private static ConfigurationService perTestConfiguration;

  /**
   * Uses the singleton pattern to ensure there is only one
   * optimize instance for all tests.
   */
  public static TestEmbeddedCamundaOptimize getInstance() {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize();
    }
    return testOptimizeInstance;
  }

  private TestEmbeddedCamundaOptimize(String contextLocation) {
    super(contextLocation);
  }

  private TestEmbeddedCamundaOptimize() {
    optimize = new TestEmbeddedCamundaOptimize(contextLocation);
    properties = PropertyUtil.loadProperties(propertiesLocation);
  }

  public void start() throws Exception {
    if (!optimize.isOptimizeStarted()) {
      optimize.startOptimize();
      storeAuthenticationToken();
      if (isThisTheFirstTimeOptimizeWasStarted()) {
        // store the default configuration to restore it later
        defaultConfiguration = new ConfigurationService();
        BeanUtils.copyProperties(optimize.getConfigurationService(), defaultConfiguration);
        perTestConfiguration = new ConfigurationService();
        BeanUtils.copyProperties(defaultConfiguration, perTestConfiguration);
      }
      BeanUtils.copyProperties(perTestConfiguration, optimize.getConfigurationService());
      reloadConfiguration();
    }
  }

  public boolean isStarted() {
    return optimize.isOptimizeStarted();
  }

  private boolean isThisTheFirstTimeOptimizeWasStarted() {
    return defaultConfiguration == null;
  }

  public void destroy() throws Exception {
    BeanUtils.copyProperties(optimize.getConfigurationService(), perTestConfiguration);
    optimize.destroyOptimize();
    testOptimizeInstance = null;
  }

  public void resetConfiguration() {
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(defaultConfiguration, optimize.getConfigurationService());
  }

  public void reloadConfiguration() {
    Map<String, ?> refreshableServices = getApplicationContext().getBeansOfType(ConfigurationReloadable.class);
    for (Map.Entry<String, ?> entry : refreshableServices.entrySet()) {
      Object beanRef = entry.getValue();
      if (beanRef instanceof ConfigurationReloadable) {
        ConfigurationReloadable reloadable = (ConfigurationReloadable) beanRef;
        reloadable.reloadConfiguration(getApplicationContext());

      }
    }
  }

  protected ApplicationContext getApplicationContext() {
    return optimize.getOptimizeApplicationContext();
  }

  public ScheduleJobFactory getImportScheduleFactory() {
    return getApplicationContext().getBean(ScheduleJobFactory.class);
  }

  public ImportJobExecutor getImportJobExecutor() {
    return getApplicationContext().getBean(ImportJobExecutor.class);
  }

  public void initializeSchema() {
    ElasticSearchSchemaInitializer schemaInitializer =
      getApplicationContext().getBean(ElasticSearchSchemaInitializer.class);
    schemaInitializer.setInitialized(false);
    schemaInitializer.initializeSchema();
  }

  public ImportServiceProvider getImportServiceProvider() {
    return getApplicationContext().getBean(ImportServiceProvider.class);
  }

  public ConfigurationService getConfigurationService() {
    return getApplicationContext().getBean(ConfigurationService.class);
  }

  /**
   * The actual storing is only performed once, when this class is the first time initialized.
   */
  private void storeAuthenticationToken() {
    if(authenticationToken == null) {
      authenticationToken = this.authenticateAdmin();
    }
  }

  public String getAuthenticationToken() {
    return authenticationToken;
  }

  private String authenticateAdmin() {
    Response tokenResponse = authenticateAdminRequest();
    return tokenResponse.readEntity(String.class);
  }

  private Response authenticateAdminRequest() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    return target()
      .path("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public WebTarget target() {
    return getClient().target(getEmbeddedOptimizeEndpoint());
  }

  public WebTarget rootTarget() {
    return getClient().target(getEmbeddedOptimizeRootEndpoint());
  }

  public final WebTarget rootTarget(String path) {
    return this.rootTarget().path(path);
  }

  public final WebTarget target(String path) {
    return this.target().path(path);
  }

  private String getEmbeddedOptimizeEndpoint() {
    return properties.getProperty("camunda.optimize.test.embedded-optimize");
  }

  private String getEmbeddedOptimizeRootEndpoint() {
    return properties.getProperty("camunda.optimize.test.embedded-optimize.root");
  }

  private Client getClient() {
    Client client = ClientBuilder.newClient();
    client.property(ClientProperties.CONNECT_TIMEOUT, 10000);
    client.property(ClientProperties.READ_TIMEOUT,    10000);
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);
    return client;
  }

  public void startImportScheduler() {
    getImportScheduler().start();
  }

  private ImportScheduler getImportScheduler() {
    return getApplicationContext().getBean(ImportScheduler.class);
  }
}
