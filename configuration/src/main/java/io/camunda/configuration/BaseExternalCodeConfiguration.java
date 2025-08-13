/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public abstract class BaseExternalCodeConfiguration {

  /** Sets the identifier of the implementation. */
  private String id;

  /**
   * Sets the path to the class JAR. Can be null if the implementation class can be found on the
   * class path.
   */
  private String jarPath;

  /**
   * Sets a new class name. Note that this must be a fully qualified class name to avoid any
   * collisions.
   */
  private String className;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

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
