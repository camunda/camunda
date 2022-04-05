/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import java.util.ArrayList;
import java.util.List;

public class ListViewResponseDto {

  private List<ListViewProcessInstanceDto> processInstances = new ArrayList<>();

  private long totalCount;

  public List<ListViewProcessInstanceDto> getProcessInstances() {
    return processInstances;
  }

  public void setProcessInstances(List<ListViewProcessInstanceDto> processInstances) {
    this.processInstances = processInstances;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewResponseDto that = (ListViewResponseDto) o;

    if (totalCount != that.totalCount)
      return false;
    return processInstances != null ? processInstances.equals(that.processInstances) : that.processInstances == null;
  }

  @Override
  public int hashCode() {
    int result = processInstances != null ? processInstances.hashCode() : 0;
    result = 31 * result + (int) (totalCount ^ (totalCount >>> 32));
    return result;
  }
}
