/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.se.config;

import java.nio.file.Path;

/**
 * Stores plugin configuration params
 *
 * @param id - plugin ID
 * @param className - fully qualified plugin implementation class name, e.g. io.camunda.MyPlugin
 * @param jarPath - file system path to JAR file with plugin implementation
 */
public record PluginConfiguration(String id, String className, Path jarPath) {
  public boolean isExternal() {
    return jarPath != null;
  }
}
