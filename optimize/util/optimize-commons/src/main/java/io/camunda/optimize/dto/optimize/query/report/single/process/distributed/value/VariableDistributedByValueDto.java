/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class VariableDistributedByValueDto implements ProcessReportDistributedByValueDto {

  protected String name;
  protected VariableType type;

  public VariableDistributedByValueDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableDistributedByValueDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableDistributedByValueDto)) {
      return false;
    }
    final VariableDistributedByValueDto other = (VariableDistributedByValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "VariableDistributedByValueDto(name=" + getName() + ", type=" + getType() + ")";
  }
}
