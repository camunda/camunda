/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public class BaseExternalCodeCfg {

  protected String id;
  protected String jarPath;
  protected String className;

  /**
   * @return true if the class must be loaded from an external JAR, false otherwise
   */
  public boolean isExternal() {
    return !isEmpty(jarPath);
  }

  /**
   * Returns a human-readable identifier, mostly for debugging purposes to differentiate instances
   * of the same implementation, for example. If not specified, defaults to the {@link #className}.
   *
   * @return a human-readable identifier, mostly for debugging purposes
   */
  public String getId() {
    return id == null ? className : id;
  }

  /**
   * @param id the new debug identifier of the implementation
   */
  public void setId(final String id) {
    this.id = id;
  }

  /**
   * Returns the path to the JAR file containing the class. Note that this may be null, as this
   * field is optional. If it is null, then the implementation is looked up within the base class
   * path.
   *
   * <p>NOTE: the path may be relative or absolute. The caller must handle both cases.
   *
   * @return a path to the JAR, or null
   */
  public String getJarPath() {
    return jarPath;
  }

  /**
   * Sets the path to the class JAR. Can be null if the implementation class can be found on the
   * class path.
   *
   * @param jarPath the new JAR path, or null
   */
  public void setJarPath(final String jarPath) {
    this.jarPath = jarPath;
  }

  /**
   * @return the fully qualified class name of the filter implementation
   */
  public String getClassName() {
    return className;
  }

  /**
   * Sets a new class name. Note that this must be a fully qualified class name to avoid any
   * collisions.
   *
   * @param className the new class name
   */
  public void setClassName(final String className) {
    this.className = className;
  }

  private boolean isEmpty(final String value) {
    return value == null || value.isEmpty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(jarPath, className);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BaseExternalCodeCfg that = (BaseExternalCodeCfg) o;
    return Objects.equals(jarPath, that.jarPath) && Objects.equals(className, that.className);
  }
}
