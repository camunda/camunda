/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.FlowNodeTotalDurationDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RawDataProcessInstanceDto implements RawDataInstanceDto {

  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected RawDataCountDto counts;
  protected Map<String, FlowNodeTotalDurationDataDto> flowNodeDurations;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long
      duration; // duration in ms. Displayed in Frontend as "Duration" with appropriate unit
  protected String engineName;
  protected String tenantId;

  // Note that for more convenient display in raw data reports, each list of variable values is
  // joined to form one
  // comma separated string
  protected Map<String, Object> variables;

  // Note that the flow node data field can only be included on the Json export response
  protected List<RawDataFlowNodeDataDto> flowNodeInstances;

  public enum Fields {
    processDefinitionKey,
    processDefinitionId,
    processInstanceId,
    businessKey,
    startDate,
    endDate,
    duration,
    engineName,
    tenantId
  }
}
