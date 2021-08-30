/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

public class InterceptorCfg {

  private String jarPath;
  private String className;

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
}
