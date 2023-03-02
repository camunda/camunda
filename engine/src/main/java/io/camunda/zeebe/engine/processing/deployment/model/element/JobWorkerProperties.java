/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.Map;

/**
 * The properties of an element that is based on a job and should be processed by a job worker. For
 * example, a service task.
 */
public class JobWorkerProperties {

  private Expression type;
  private Expression retries;
  private Expression assignee;
  private Expression candidateGroups;
  private Expression candidateUsers;
  private Expression dueDate;
  private Expression followUpDate;
  private Map<String, String> taskHeaders = Map.of();

  public Expression getType() {
    return type;
  }

  public void setType(final Expression type) {
    this.type = type;
  }

  public Expression getRetries() {
    return retries;
  }

  public void setRetries(final Expression retries) {
    this.retries = retries;
  }

  public Expression getAssignee() {
    return assignee;
  }

  public void setAssignee(final Expression assignee) {
    this.assignee = assignee;
  }

  public Expression getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final Expression candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public Expression getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final Expression candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  public Expression getDueDate() {
    return dueDate;
  }

  public void setDueDate(final Expression dueDate) {
    this.dueDate = dueDate;
  }

  public Expression getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(final Expression followUpDate) {
    this.followUpDate = followUpDate;
  }

  public Map<String, String> getTaskHeaders() {
    return taskHeaders;
  }

  public void setTaskHeaders(final Map<String, String> taskHeaders) {
    this.taskHeaders = taskHeaders;
  }
}
