/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RawDataProcessInstanceDto {
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long durationInMs;
  protected String engineName;
  protected String tenantId;
  protected Map<String, Object> variables;
}
