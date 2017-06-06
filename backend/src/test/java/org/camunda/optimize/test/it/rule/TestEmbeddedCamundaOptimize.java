package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.util.ConfigurationReloadable;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * This class is wrapper around the embedded optimize to ensure
 * only one instance is used for all tests. Also makes sure the
 * configuration is reset after each test.
 *
 * @author Askar Akhmerov
 */
public class TestEmbeddedCamundaOptimize {

  private final static String contextLocation = "classpath:embeddedOptimizeContext.xml";

  private static EmbeddedCamundaOptimize optimize;

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

  private TestEmbeddedCamundaOptimize() {
    optimize = new EmbeddedCamundaOptimize(contextLocation);
  }

  public void start() throws Exception {
    if (!optimize.isStarted()) {
      optimize.start();
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
    return optimize.isStarted();
  }

  private boolean isThisTheFirstTimeOptimizeWasStarted() {
    return defaultConfiguration == null;
  }

  public void destroy() throws Exception {
    BeanUtils.copyProperties(optimize.getConfigurationService(), perTestConfiguration);
    optimize.destroy();
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

  public ApplicationContext getApplicationContext() {
    return optimize.getApplicationContext();
  }

  public ScheduleJobFactory getImportScheduleFactory() {
    return getApplicationContext().getBean(ScheduleJobFactory.class);
  }

  public ImportJobExecutor getImportJobExecutor() {
    return getApplicationContext().getBean(ImportJobExecutor.class);
  }

  public ImportServiceProvider getImportServiceProvider() {
    return getApplicationContext().getBean(ImportServiceProvider.class);
  }

  public ConfigurationService getConfigurationService() {
    return getApplicationContext().getBean(ConfigurationService.class);
  }

  public void initializeIndex() {
    getApplicationContext().getBean(ElasticSearchSchemaInitializer.class).initializeSchema();
  }

  public void startImportScheduler() {
    getImportScheduler().start();
  }

  private ImportScheduler getImportScheduler() {
    return getApplicationContext().getBean(ImportScheduler.class);
  }
}
