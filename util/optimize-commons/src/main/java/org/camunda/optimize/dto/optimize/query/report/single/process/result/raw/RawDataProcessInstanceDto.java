/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;

import java.time.OffsetDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldNameConstants(asEnum = true)
public class RawDataProcessInstanceDto implements RawDataInstanceDto {
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long duration; // duration in ms. Displayed in Frontend as "Duration" with appropriate unit
  protected String engineName;
  protected String tenantId;
  @FieldNameConstants.Exclude
  // Note that for more convenient display in raw data reports, each list of variable values is joined to form one
  // comma separated string
  protected Map<String, Object> variables;
}
