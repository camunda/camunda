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
  protected String id;

  /**
   * Sets the path to the class JAR. Can be null if the implementation class can be found on the
   * class path.
   */
  protected String jarPath;

  /**
   * Sets a new class name. Note that this must be a fully qualified class name to avoid any
   * collisions.
   */
  protected String className;

  public void setId(final String id) {
    this.id = id;
  }

  public abstract String getId(final int index);

  public void setJarPath(final String jarPath) {
    this.jarPath = jarPath;
  }

  public abstract String getJarPath(final int index);

  public void setClassName(final String className) {
    this.className = className;
  }

  public abstract String getClassName(final int index);
}
