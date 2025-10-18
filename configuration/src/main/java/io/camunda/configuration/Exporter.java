/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;

public class Exporter {

  /**
   * path to the JAR file containing the exporter class
   *
   * <p>optional field: if missing, will lookup the class in the zeebe classpath
   */
  private String jarPath;

  /** fully qualified class name pointing to the class implementing the exporter interface */
  private String className;

  /** map of arguments to use when instantiating the exporter */
  private Map<String, Object> args;

  public String getJarPath() {
    return jarPath;
  }

  public void setJarPath(final String jarPath) {
    this.jarPath = jarPath;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(final String className) {
    this.className = className;
  }

  public Map<String, Object> getArgs() {
    return args;
  }

  public void setArgs(final Map<String, Object> args) {
    this.args = args;
  }

  public ExporterCfg toExporterCfg() {
    final var exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(className);
    exporterCfg.setJarPath(jarPath);
    exporterCfg.setArgs(args);
    return exporterCfg;
  }
}
