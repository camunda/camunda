/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import static io.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.INTEGER_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.INTEGER_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.LONG_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.LONG_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.SHORT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.SHORT_TYPE_LOWERCASE;
import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE_LOWERCASE;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DashboardStringVariableFilterDataDto.class, name = STRING_TYPE),
  @JsonSubTypes.Type(
      value = DashboardStringVariableFilterDataDto.class,
      name = STRING_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardShortVariableFilterDataDto.class, name = SHORT_TYPE),
  @JsonSubTypes.Type(
      value = DashboardShortVariableFilterDataDto.class,
      name = SHORT_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardLongVariableFilterDataDto.class, name = LONG_TYPE),
  @JsonSubTypes.Type(value = DashboardLongVariableFilterDataDto.class, name = LONG_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardDoubleVariableFilterDataDto.class, name = DOUBLE_TYPE),
  @JsonSubTypes.Type(
      value = DashboardDoubleVariableFilterDataDto.class,
      name = DOUBLE_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardIntegerVariableFilterDataDto.class, name = INTEGER_TYPE),
  @JsonSubTypes.Type(
      value = DashboardIntegerVariableFilterDataDto.class,
      name = INTEGER_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardBooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE),
  @JsonSubTypes.Type(
      value = DashboardBooleanVariableFilterDataDto.class,
      name = BOOLEAN_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardDateVariableFilterDataDto.class, name = DATE_TYPE),
  @JsonSubTypes.Type(value = DashboardDateVariableFilterDataDto.class, name = DATE_TYPE_LOWERCASE)
})
public abstract class DashboardVariableFilterDataDto implements FilterDataDto {

  protected VariableType type;
  protected String name;
  protected DashboardVariableFilterSubDataDto data;

  protected DashboardVariableFilterDataDto(
      final VariableType type, final String name, final DashboardVariableFilterSubDataDto data) {
    this.name = name;
    this.type = type;
    this.data = data;
  }

  protected DashboardVariableFilterDataDto() {}

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public DashboardVariableFilterSubDataDto getData() {
    return data;
  }

  public void setData(final DashboardVariableFilterSubDataDto data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardVariableFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardVariableFilterDataDto)) {
      return false;
    }
    final DashboardVariableFilterDataDto other = (DashboardVariableFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DashboardVariableFilterDataDto(type="
        + getType()
        + ", name="
        + getName()
        + ", data="
        + getData()
        + ")";
  }
}
