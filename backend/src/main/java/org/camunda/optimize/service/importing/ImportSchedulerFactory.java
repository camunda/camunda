package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.util.AbstractParametrizedFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A Parametrized Factory class to obtain import scheduler threads based on engine name.
 *
 * @author Askar Akhmerov
 */
@Component
public class ImportSchedulerFactory
    extends AbstractParametrizedFactory<ImportScheduler, String>
    implements ConfigurationReloadable {

  private ConfigurationService configurationService;
  private final ApplicationContext applicationContext;

  @Autowired
  public ImportSchedulerFactory(ConfigurationService configurationService, ApplicationContext applicationContext) {
    this.configurationService = configurationService;
    this.applicationContext = applicationContext;
    this.init();
  }

  private void init() {
    for (Map.Entry<String,EngineConfiguration> engine : configurationService.getConfiguredEngines().entrySet()) {
      ImportScheduler instance = this.getInstance(engine.getKey());
      applicationContext.getAutowireCapableBeanFactory().autowireBean(instance);
    }
  }

  @Override
  protected ImportScheduler newInstance(String engineAlias) {
    return new ImportScheduler(engineAlias);
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    this.init();
  }
}
