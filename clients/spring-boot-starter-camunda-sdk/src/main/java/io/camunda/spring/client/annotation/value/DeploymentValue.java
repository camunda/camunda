/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.annotation.value;

import io.camunda.spring.client.bean.ClassInfo;
import java.util.List;
import java.util.Objects;

public final class DeploymentValue implements CamundaAnnotationValue<ClassInfo> {

  private final List<String> resources;

  private final ClassInfo beanInfo;

  private DeploymentValue(final List<String> resources, final ClassInfo beanInfo) {
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
  public int hashCode() {
    return Objects.hash(resources, beanInfo);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentValue that = (DeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(beanInfo, that.beanInfo);
  }

  @Override
  public String toString() {
    return "DeploymentValue{" +
        "resources=" + resources +
        ", beanInfo=" + beanInfo +
        '}';
  }

  public static DeploymentValueBuilder builder() {
    return new DeploymentValueBuilder();
  }

  public static final class DeploymentValueBuilder {

    private List<String> resources;
    private ClassInfo beanInfo;

    private DeploymentValueBuilder() {}

    public DeploymentValueBuilder resources(final List<String> resources) {
      this.resources = resources;
      return this;
    }

    public DeploymentValueBuilder beanInfo(final ClassInfo beanInfo) {
      this.beanInfo = beanInfo;
      return this;
    }

    public DeploymentValue build() {
      return new DeploymentValue(resources, beanInfo);
    }
  }
}
