package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.camunda.optimize.service.engine.importing.EngineImportJobExecutor;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.format.DateTimeFormatter;
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

  private final static String DEFAULT_CONTEXT_LOCATION = "classpath:embeddedOptimizeContext.xml";
  private final static String propertiesLocation = "integration-rules.properties";

  private static String authenticationToken;
  private Properties properties;

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
    return getInstance(DEFAULT_CONTEXT_LOCATION);
  }

  /**
   * If instance is not initialized, initialize it from specific context. Otherwise
   * return existing instance.
   *
   * @param contextLocation - must be not null
   * @return static instance of embedded Optimize
   */
  private static TestEmbeddedCamundaOptimize getInstance(String contextLocation) {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize(contextLocation);
    }
    return testOptimizeInstance;
  }

  private TestEmbeddedCamundaOptimize(String contextLocation) {
    super(contextLocation);
    properties = PropertyUtil.loadProperties(propertiesLocation);
  }

  public void start() throws Exception {
    if (!testOptimizeInstance.isOptimizeStarted()) {
      testOptimizeInstance.startOptimize();
      storeAuthenticationToken();
      if (isThisTheFirstTimeOptimizeWasStarted()) {
        // store the default configuration to restore it later
        defaultConfiguration = new ConfigurationService();
        BeanUtils.copyProperties(testOptimizeInstance.getConfigurationService(), defaultConfiguration);
        perTestConfiguration = new ConfigurationService();
        BeanUtils.copyProperties(defaultConfiguration, perTestConfiguration);
      }
      BeanUtils.copyProperties(perTestConfiguration, testOptimizeInstance.getConfigurationService());
      reloadConfiguration();
    }
  }

  public boolean isStarted() {
    return testOptimizeInstance.isOptimizeStarted();
  }

  private boolean isThisTheFirstTimeOptimizeWasStarted() {
    return defaultConfiguration == null;
  }

  public void destroy() throws Exception {
    BeanUtils.copyProperties(testOptimizeInstance.getConfigurationService(), perTestConfiguration);
    testOptimizeInstance.destroyOptimize();
    testOptimizeInstance = null;
  }

  public void resetConfiguration() {
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(defaultConfiguration, testOptimizeInstance.getConfigurationService());
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
    return testOptimizeInstance.getOptimizeApplicationContext();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getApplicationContext().getBean(ElasticsearchImportJobExecutor.class);
  }

  public EngineImportJobExecutor getEngineImportJobExecutor() {
    return getApplicationContext().getBean(EngineImportJobExecutor.class);
  }

  public ConfigurationService getConfigurationService() {
    return getApplicationContext().getBean(ConfigurationService.class);
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getApplicationContext().getBean(DateTimeFormatter.class);
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

  public void initializeSchema() {
    ElasticSearchSchemaInitializer schemaInitializer =
      getApplicationContext().getBean(ElasticSearchSchemaInitializer.class);
    schemaInitializer.setInitialized(false);
    schemaInitializer.initializeSchema();
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
    // register the default object provider for serialization/deserialization ob objects
    OptimizeObjectMapperProvider provider = getApplicationContext()
        .getBean(OptimizeObjectMapperProvider.class);

    Client client = ClientBuilder.newClient()
      .register(provider);
    client.property(ClientProperties.CONNECT_TIMEOUT, 10000);
    client.property(ClientProperties.READ_TIMEOUT,    10000);
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);
    return client;
  }

}
