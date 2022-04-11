/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FixedFlowNodeDateFilterDataDto.class, name = FIXED_DATE_FILTER),
  @JsonSubTypes.Type(value = RollingFlowNodeDateFilterDataDto.class, name = ROLLING_DATE_FILTER),
  @JsonSubTypes.Type(value = RelativeFlowNodeDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
})
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public abstract class FlowNodeDateFilterDataDto<START> extends DateFilterDataDto<START> {

  protected List<String> flowNodeIds;

  protected FlowNodeDateFilterDataDto(final List<String> flowNodeIds, final DateFilterType type,
                                      final START start, final OffsetDateTime end) {
    super(type, start, end);
    this.flowNodeIds = flowNodeIds;
  }
}
