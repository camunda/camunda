/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.FilterEntity;
import io.camunda.tasklist.entities.TaskFilterEntity;
import java.util.List;

public class AddFilterRequest {

  private String name;
  private String filter;
  private String createdBy;
  private List<String> candidateUsers;

  public TaskFilterEntity toFilterEntity(){
    TaskFilterEntity filterEntity = new TaskFilterEntity();
    filterEntity.setName(this.getName());
    filterEntity.setFilter(this.getFilter());
    filterEntity.setCandidateGroups(this.getCandidateGroups());
    filterEntity.setCandidateUsers(this.getCandidateUsers());
    filterEntity.setCreatedBy(this.getCreatedBy());
    return filterEntity;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  private List<String> candidateGroups;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(final String filter) {
    this.filter = filter;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(final String user) {
    this.createdBy = user;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
  }
}
