package org.camunda.optimize.service.util;

import org.springframework.context.ApplicationContext;

/**
 * Every class that implements this interfaces is automatically
 * called as soon as {TestEmbeddedCamundaOptimize#reloadConfiguration} is called.
 */
public interface ConfigurationReloadable {

  /**
   * This method reloads the configuration for the implementing class.
   * @param context Application context of the embedded optimize.
   */
  void reloadConfiguration(ApplicationContext context);
}
