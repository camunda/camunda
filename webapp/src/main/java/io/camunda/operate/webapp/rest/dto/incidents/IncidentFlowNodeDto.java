/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

public class IncidentFlowNodeDto {

  private String id;

  private int count;

  public IncidentFlowNodeDto() {
  }

  public IncidentFlowNodeDto(String flowNodeId, int count) {
    this.id = flowNodeId;
    this.count = count;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentFlowNodeDto that = (IncidentFlowNodeDto) o;

    if (count != that.count)
      return false;
    return id != null ? id.equals(that.id) : that.id == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + count;
    return result;
  }
}
