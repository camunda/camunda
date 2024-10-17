/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

public class OpenIncident {
  private String type;
  private String message;

  private String flowNodeId;
  private String flowNodeName;

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public String getFlowNodeName() {
    return flowNodeName;
  }

  public void setFlowNodeName(final String flowNodeName) {
    this.flowNodeName = flowNodeName;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }
}
