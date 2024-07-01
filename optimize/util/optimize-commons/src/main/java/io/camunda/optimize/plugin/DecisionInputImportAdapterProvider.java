/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin;

import io.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DecisionInputImportAdapterProvider extends PluginProvider<DecisionInputImportAdapter> {

  public DecisionInputImportAdapterProvider(
      final ConfigurationService configurationService, final PluginJarFileLoader pluginJarLoader) {
    super(configurationService, pluginJarLoader);
  }

  @Override
  protected Class<DecisionInputImportAdapter> getPluginClass() {
    return DecisionInputImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getDecisionInputImportPluginBasePackages();
  }
}
