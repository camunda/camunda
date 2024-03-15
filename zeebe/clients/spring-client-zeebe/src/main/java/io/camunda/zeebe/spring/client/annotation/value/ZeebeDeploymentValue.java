/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.util.List;
import java.util.Objects;

public class ZeebeDeploymentValue implements ZeebeAnnotationValue<ClassInfo> {

  private final List<String> resources;

  private final ClassInfo beanInfo;

  private ZeebeDeploymentValue(final List<String> resources, final ClassInfo beanInfo) {
    this.resources = resources;
    this.beanInfo = beanInfo;
  }

  public List<String> getResources() {
    return resources;
  }

  @Override
  public ClassInfo getBeanInfo() {
    return beanInfo;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ZeebeDeploymentValue that = (ZeebeDeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(beanInfo, that.beanInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resources, beanInfo);
  }

  @Override
  public String toString() {
    return "ZeebeDeploymentValue{" + "resources=" + resources + ", beanInfo=" + beanInfo + '}';
  }

  public static ZeebeDeploymentValueBuilder builder() {
    return new ZeebeDeploymentValueBuilder();
  }

  public static final class ZeebeDeploymentValueBuilder {

    private List<String> resources;
    private ClassInfo beanInfo;

    private ZeebeDeploymentValueBuilder() {}

    public ZeebeDeploymentValueBuilder resources(final List<String> resources) {
      this.resources = resources;
      return this;
    }

    public ZeebeDeploymentValueBuilder beanInfo(final ClassInfo beanInfo) {
      this.beanInfo = beanInfo;
      return this;
    }

    public ZeebeDeploymentValue build() {
      return new ZeebeDeploymentValue(resources, beanInfo);
    }
  }
}
