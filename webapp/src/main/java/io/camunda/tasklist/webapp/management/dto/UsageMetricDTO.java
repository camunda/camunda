/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.management.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UsageMetricDTO {
  /** Uniquely assigned users */
  private List<String> assignees;
  /** Total amount retrived in the current search */
  private int total;
  /** Total amount of unique users in the entire period */
  private int assignedUsersInPeriodCount;
  /** Pagination values */
  private String[] sortValues;

  public List<String> getAssignees() {
    return assignees;
  }

  public void setAssignees(List<String> assignees) {
    this.assignees = assignees;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getAssignedUsersInPeriodCount() {
    return assignedUsersInPeriodCount;
  }

  public void setAssignedUsersInPeriodCount(int assignedUsersInPeriodCount) {
    this.assignedUsersInPeriodCount = assignedUsersInPeriodCount;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public void setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UsageMetricDTO)) {
      return false;
    }
    final UsageMetricDTO that = (UsageMetricDTO) o;
    return total == that.total
        && assignedUsersInPeriodCount == that.assignedUsersInPeriodCount
        && Objects.equals(assignees, that.assignees)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(assignees, total, assignedUsersInPeriodCount);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
