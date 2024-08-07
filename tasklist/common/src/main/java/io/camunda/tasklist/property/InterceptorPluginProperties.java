/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class InterceptorPluginProperties {

  private String id;
  private String className;
  private String jarPath;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(final String className) {
    this.className = className;
  }

  public String getJarPath() {
    return jarPath;
  }

  public void setJarPath(final String jarPath) {
    this.jarPath = jarPath;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(id).append(className).append(jarPath).toHashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final InterceptorPluginProperties that = (InterceptorPluginProperties) o;

    return new EqualsBuilder()
        .append(id, that.id)
        .append(className, that.className)
        .append(jarPath, that.jarPath)
        .isEquals();
  }

  @Override
  public String toString() {
    return "InterceptorPluginProperties{"
        + "id='"
        + id
        + '\''
        + ", className='"
        + className
        + '\''
        + ", jarPath='"
        + jarPath
        + '\''
        + '}';
  }
}
