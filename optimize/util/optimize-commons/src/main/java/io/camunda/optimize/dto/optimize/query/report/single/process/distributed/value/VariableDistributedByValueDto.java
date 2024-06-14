/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import lombok.Data;

@Data
public class VariableDistributedByValueDto implements ProcessReportDistributedByValueDto {
  protected String name;
  protected VariableType type;
}
