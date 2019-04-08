/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImportAdapterProvider extends PluginProvider<VariableImportAdapter> {

  @Override
  protected Class<VariableImportAdapter> getPluginClass() {
    return VariableImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getVariableImportPluginBasePackages();
  }

}
