/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EngineRestFilterProvider extends PluginProvider<EngineRestFilter> {

  public EngineRestFilterProvider(final ConfigurationService configurationService,
                                  final PluginJarFileLoader pluginJarLoader) {
    super(configurationService, pluginJarLoader);
  }

  @Override
  protected Class<EngineRestFilter> getPluginClass() {
    return EngineRestFilter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getEngineRestFilterPluginBasePackages();
  }

}
