/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.businesskey.BusinessKeyImportAdapter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessKeyImportAdapterProvider extends PluginProvider<BusinessKeyImportAdapter> {

  public BusinessKeyImportAdapterProvider(final ConfigurationService configurationService,
                                          final PluginJarFileLoader pluginJarLoader) {
    super(configurationService, pluginJarLoader);
  }

  @Override
  protected Class<BusinessKeyImportAdapter> getPluginClass() {
    return BusinessKeyImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getBusinessKeyImportPluginBasePackages();
  }

}
