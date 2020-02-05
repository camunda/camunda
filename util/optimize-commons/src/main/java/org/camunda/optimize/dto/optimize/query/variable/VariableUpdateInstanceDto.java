/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Builder
@Getter
public class VariableUpdateInstanceDto {

  private String instanceId;
  private String name;
  private String type;
  private String value;
  private String processInstanceId;
  private String tenantId;
  private OffsetDateTime timestamp;

}
