/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
