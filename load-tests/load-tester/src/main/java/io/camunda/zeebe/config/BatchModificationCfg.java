/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

public class BatchModificationCfg {

  private int instanceCount;
  private String sourceElement;
  private String targetElement;

  public int getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final int instanceCount) {
    this.instanceCount = instanceCount;
  }

  public String getSourceElement() {
    return sourceElement;
  }

  public void setSourceElement(final String sourceElement) {
    this.sourceElement = sourceElement;
  }

  public String getTargetElement() {
    return targetElement;
  }

  public void setTargetElement(final String targetElement) {
    this.targetElement = targetElement;
  }
}
