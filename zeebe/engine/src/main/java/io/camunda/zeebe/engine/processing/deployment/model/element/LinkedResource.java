/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;

public class LinkedResource {
  private String resourceId;
  private String resourceType;
  private String linkName;
  private ZeebeBindingType bindingType;
  private String versionTag;

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(final String resourceType) {
    this.resourceType = resourceType;
  }

  public String getLinkName() {
    return linkName;
  }

  public void setLinkName(final String linkName) {
    this.linkName = linkName;
  }

  public void setBindingType(final ZeebeBindingType bindingType) {
    this.bindingType = bindingType;
  }

  public ZeebeBindingType getBindingType() {
    return bindingType;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }
}
