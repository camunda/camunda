/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.util.Objects;

public class ProcessFlowNodeEntity {

  private String id;
  private String name;

  public ProcessFlowNodeEntity() {}

  public ProcessFlowNodeEntity(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public ProcessFlowNodeEntity setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessFlowNodeEntity setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProcessFlowNodeEntity that = (ProcessFlowNodeEntity) o;

    if (!Objects.equals(id, that.id)) {
      return false;
    }
    return Objects.equals(name, that.name);
  }
}
