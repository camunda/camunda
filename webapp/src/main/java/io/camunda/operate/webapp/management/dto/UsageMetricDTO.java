/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import java.util.Objects;

public class UsageMetricDTO {
  private long total;

  public long getTotal() {
    return total;
  }

  public UsageMetricDTO setTotal(long total) {
    this.total = total;
    return this;
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
    return total == that.total;
  }

  @Override
  public int hashCode() {
    return Objects.hash(total);
  }

  @Override
  public String toString() {
    return "UsageMetricDTO{total=" + total + '}';
  }
}
