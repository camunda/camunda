/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.pluginloading;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;

/**
 * This class is used to test if this class inside Optimize wins in the class loading over the class
 * from the plugin with the same name and package
 */
public class SharedTestPluginVariableDto extends PluginVariableDto {

  @Override
  public String getId() {
    return "optimize-class";
  }
}
