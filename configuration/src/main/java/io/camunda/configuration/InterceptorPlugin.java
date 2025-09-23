/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.search.connect.plugin.PluginConfiguration;
import java.nio.file.Path;

public class InterceptorPlugin extends BaseExternalCodeConfiguration {

  public PluginConfiguration toPluginConfiguration() {
    return new PluginConfiguration(getId(), getClassName(), Path.of(getJarPath()));
  }
}
