/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.management.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UsageMetricDTO {
  /** Uniquely assigned users */
  private List<String> assignees;
  /** Total amount retrived in the current search */
  private int total;

  public UsageMetricDTO() {}

  public UsageMetricDTO(List<String> assignees) {
    this.assignees = Objects.requireNonNullElseGet(assignees, ArrayList::new);

    this.total = this.assignees.size();
  }

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UsageMetricDTO)) {
      return false;
    }
    final UsageMetricDTO that = (UsageMetricDTO) o;
    return total == that.total && Objects.equals(assignees, that.assignees);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assignees, total);
  }

  @Override
  public String toString() {
    return "UsageMetricDTO{" + "assignees=" + assignees + ", total=" + total + '}';
  }
}
