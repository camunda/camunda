/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class RawDataFlowNodeDataDto implements RawDataInstanceDto {
  private String id;
  private String name;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
}
