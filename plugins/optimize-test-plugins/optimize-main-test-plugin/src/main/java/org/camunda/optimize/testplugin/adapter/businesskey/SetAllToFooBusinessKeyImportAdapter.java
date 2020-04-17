/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.adapter.businesskey;

import org.camunda.optimize.plugin.importing.businesskey.BusinessKeyImportAdapter;
import org.camunda.optimize.plugin.importing.businesskey.PluginProcessInstanceDto;

import java.util.List;

public class SetAllToFooBusinessKeyImportAdapter implements BusinessKeyImportAdapter {
  private static String newBusinessKey = "foo";

  @Override
  public List<PluginProcessInstanceDto> adaptBusinessKeys(final List<PluginProcessInstanceDto> processInstances) {
    processInstances.stream()
      .forEach(instance -> instance.setBusinessKey(newBusinessKey));
    return processInstances;
  }
}
