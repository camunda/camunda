/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.filter.data;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.INTEGER_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.INTEGER_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.LONG_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.LONG_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.SHORT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.SHORT_TYPE_LOWERCASE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE_LOWERCASE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DashboardStringVariableFilterDataDto.class, name = STRING_TYPE),
  @JsonSubTypes.Type(value = DashboardStringVariableFilterDataDto.class, name = STRING_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardShortVariableFilterDataDto.class, name = SHORT_TYPE),
  @JsonSubTypes.Type(value = DashboardShortVariableFilterDataDto.class, name = SHORT_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardLongVariableFilterDataDto.class, name = LONG_TYPE),
  @JsonSubTypes.Type(value = DashboardLongVariableFilterDataDto.class, name = LONG_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardDoubleVariableFilterDataDto.class, name = DOUBLE_TYPE),
  @JsonSubTypes.Type(value = DashboardDoubleVariableFilterDataDto.class, name = DOUBLE_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardIntegerVariableFilterDataDto.class, name = INTEGER_TYPE),
  @JsonSubTypes.Type(value = DashboardIntegerVariableFilterDataDto.class, name = INTEGER_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardBooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE),
  @JsonSubTypes.Type(value = DashboardBooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE_LOWERCASE),
  @JsonSubTypes.Type(value = DashboardDateVariableFilterDataDto.class, name = DATE_TYPE),
  @JsonSubTypes.Type(value = DashboardDateVariableFilterDataDto.class, name = DATE_TYPE_LOWERCASE)
})
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DashboardVariableFilterDataDto implements FilterDataDto {

  protected VariableType type;
  protected String name;
  protected DashboardVariableFilterSubDataDto data;

  protected DashboardVariableFilterDataDto(final VariableType type, final String name,
                                           final DashboardVariableFilterSubDataDto data) {
    this.name = name;
    this.type = type;
    this.data = data;
  }
}
