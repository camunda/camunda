/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class TermsQueryProperties {

  private int maxWorkflowCount = 100;
  private int maxUniqueBpmnProcessIdCount = 50;
  private int maxVersionOfOneWorkflowCount = 50;
  private int maxIncidentErrorMessageCount = 100;
  private int maxFlowNodesInOneWorkflow = 200;

  public int getMaxWorkflowCount() {
    return maxWorkflowCount;
  }

  public void setMaxWorkflowCount(int maxWorkflowCount) {
    this.maxWorkflowCount = maxWorkflowCount;
  }

  public int getMaxUniqueBpmnProcessIdCount() {
    return maxUniqueBpmnProcessIdCount;
  }

  public void setMaxUniqueBpmnProcessIdCount(int maxUniqueBpmnProcessIdCount) {
    this.maxUniqueBpmnProcessIdCount = maxUniqueBpmnProcessIdCount;
  }

  public int getMaxVersionOfOneWorkflowCount() {
    return maxVersionOfOneWorkflowCount;
  }

  public void setMaxVersionOfOneWorkflowCount(int maxVersionOfOneWorkflowCount) {
    this.maxVersionOfOneWorkflowCount = maxVersionOfOneWorkflowCount;
  }

  public int getMaxIncidentErrorMessageCount() {
    return maxIncidentErrorMessageCount;
  }

  public void setMaxIncidentErrorMessageCount(int maxIncidentErrorMessageCount) {
    this.maxIncidentErrorMessageCount = maxIncidentErrorMessageCount;
  }

  public int getMaxFlowNodesInOneWorkflow() {
    return maxFlowNodesInOneWorkflow;
  }

  public void setMaxFlowNodesInOneWorkflow(int maxFlowNodesInOneWorkflow) {
    this.maxFlowNodesInOneWorkflow = maxFlowNodesInOneWorkflow;
  }
}
