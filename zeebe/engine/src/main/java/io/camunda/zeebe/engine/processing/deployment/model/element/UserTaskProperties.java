/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import java.util.Map;

/** The properties of a user task element. */
public class UserTaskProperties {

  private Expression assignee;
  private Expression candidateGroups;
  private Expression candidateUsers;
  private Expression dueDate;
  private Expression externalFormReference;
  private Expression followUpDate;
  private Expression formId;
  private Expression priority;
  private Map<String, String> taskHeaders = Map.of();
  private ZeebeBindingType formBindingType;
  private String formVersionTag;

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

  public Expression getExternalFormReference() {
    return externalFormReference;
  }

  public void setExternalFormReference(final Expression externalFormReference) {
    this.externalFormReference = externalFormReference;
  }

  public Expression getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(final Expression followUpDate) {
    this.followUpDate = followUpDate;
  }

  public Expression getFormId() {
    return formId;
  }

  public void setFormId(final Expression formId) {
    this.formId = formId;
  }

  public Map<String, String> getTaskHeaders() {
    return taskHeaders;
  }

  public void setTaskHeaders(final Map<String, String> taskHeaders) {
    this.taskHeaders = taskHeaders;
  }

  public ZeebeBindingType getFormBindingType() {
    return formBindingType;
  }

  public void setFormBindingType(final ZeebeBindingType bindingType) {
    formBindingType = bindingType;
  }

  public Expression getPriority() {
    return priority;
  }

  public void setPriority(final Expression priority) {
    this.priority = priority;
  }

  public String getFormVersionTag() {
    return formVersionTag;
  }

  public void setFormVersionTag(final String versionTag) {
    formVersionTag = versionTag;
  }

  public void wrap(final UserTaskProperties userTaskProperties) {
    setAssignee(userTaskProperties.getAssignee());
    setCandidateGroups(userTaskProperties.getCandidateGroups());
    setCandidateUsers(userTaskProperties.getCandidateUsers());
    setDueDate(userTaskProperties.getDueDate());
    setExternalFormReference(userTaskProperties.getExternalFormReference());
    setFollowUpDate(userTaskProperties.getFollowUpDate());
    setFormId(userTaskProperties.getFormId());
    setTaskHeaders(userTaskProperties.getTaskHeaders());
    setFormBindingType(userTaskProperties.getFormBindingType());
    setPriority(userTaskProperties.getPriority());
    setFormVersionTag(userTaskProperties.getFormVersionTag());
  }
}
