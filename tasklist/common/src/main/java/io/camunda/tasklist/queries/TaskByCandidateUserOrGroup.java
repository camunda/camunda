/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.queries;

import java.util.Arrays;
import java.util.Objects;

public class TaskByCandidateUserOrGroup {
  private String[] userGroups;
  private String userName;

  public TaskByCandidateUserOrGroup() {}

  public String[] getUserGroups() {
    return userGroups;
  }

  public TaskByCandidateUserOrGroup setUserGroups(String[] userGroups) {
    this.userGroups = userGroups;
    return this;
  }

  public String getUserName() {
    return userName;
  }

  public TaskByCandidateUserOrGroup setUserName(String userName) {
    this.userName = userName;
    return this;
  }

  @Override
  public String toString() {
    return "TaskByCandidateUserOrGroup{"
        + "userGroup="
        + userGroups
        + ", user='"
        + userName
        + '\''
        + '}';
  }

  public TaskByCandidateUserOrGroup createCopy() {
    return new TaskByCandidateUserOrGroup()
        .setUserGroups(this.getUserGroups())
        .setUserName(this.getUserName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskByCandidateUserOrGroup taskByCandidateUserOrGroup = (TaskByCandidateUserOrGroup) o;
    return Objects.equals(userName, taskByCandidateUserOrGroup.userName)
        && Arrays.equals(userGroups, taskByCandidateUserOrGroup.userGroups);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(userName);
    result = 31 * result + Arrays.hashCode(userGroups);
    return result;
  }
}
