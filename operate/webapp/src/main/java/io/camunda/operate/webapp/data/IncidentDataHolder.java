/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.data;

public class IncidentDataHolder {
  private String incidentId;
  private String finalFlowNodeInstanceId;
  private String finalFlowNodeId;

  public String getIncidentId() {
    return incidentId;
  }

  public IncidentDataHolder setIncidentId(final String incidentId) {
    this.incidentId = incidentId;
    return this;
  }

  public String getFinalFlowNodeInstanceId() {
    return finalFlowNodeInstanceId;
  }

  public IncidentDataHolder setFinalFlowNodeInstanceId(final String finalFlowNodeInstanceId) {
    this.finalFlowNodeInstanceId = finalFlowNodeInstanceId;
    return this;
  }

  public String getFinalFlowNodeId() {
    return finalFlowNodeId;
  }

  public IncidentDataHolder setFinalFlowNodeId(final String finalFlowNodeId) {
    this.finalFlowNodeId = finalFlowNodeId;
    return this;
  }
}
