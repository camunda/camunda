/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.plugin;

import io.camunda.optimize.plugin.importing.variable.DecisionOutputImportAdapter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DecisionOutputImportAdapterProvider
    extends PluginProvider<DecisionOutputImportAdapter> {

  public DecisionOutputImportAdapterProvider(
      final ConfigurationService configurationService, final PluginJarFileLoader pluginJarLoader) {
    super(configurationService, pluginJarLoader);
  }

  @Override
  protected Class<DecisionOutputImportAdapter> getPluginClass() {
    return DecisionOutputImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getDecisionOutputImportPluginBasePackages();
  }
}
