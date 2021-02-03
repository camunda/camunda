/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.record.value.deployment;

import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import java.util.Arrays;
import java.util.Objects;

public class DeploymentResourceImpl implements DeploymentResource {

  private byte[] resource;
  private ResourceType resourceType;
  private String resourceName;

  public DeploymentResourceImpl() {}

  @Override
  public byte[] getResource() {
    return resource;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setResourceType(ResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public void setResource(byte[] resource) {
    this.resource = resource;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(resourceType, resourceName);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentResourceImpl that = (DeploymentResourceImpl) o;
    return Arrays.equals(resource, that.resource)
        && resourceType == that.resourceType
        && Objects.equals(resourceName, that.resourceName);
  }

  @Override
  public String toString() {
    return "DeploymentResourceImpl{"
        + "resource="
        + Arrays.toString(resource)
        + ", resourceType="
        + resourceType
        + ", resourceName='"
        + resourceName
        + '\''
        + '}';
  }
}
