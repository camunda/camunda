/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.pluginloading;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;

/**
 * This class is used to test if this class inside Optimize wins in the class loading over the class from the plugin
 * with the same name and package
 */
public class SharedTestPluginVariableDto extends PluginVariableDto {

  @Override
  public String getId() {
    return "optimize-class";
  }
}
