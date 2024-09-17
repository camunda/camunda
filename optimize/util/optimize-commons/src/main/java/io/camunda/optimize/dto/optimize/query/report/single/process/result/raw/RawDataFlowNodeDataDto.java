/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class RawDataFlowNodeDataDto implements RawDataInstanceDto {

  private String id;
  private String name;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  public RawDataFlowNodeDataDto(
      String id, String name, OffsetDateTime startDate, OffsetDateTime endDate) {
    this.id = id;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public RawDataFlowNodeDataDto() {}
}
