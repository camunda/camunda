/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(asEnum = true)
public class CustomNumberBucketDto {
  private boolean active = false;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double bucketSize = 10.0;

  // baseline = start of first bucket for number var reports. If left null, the bucket range will start at the min.
  // variable value
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double baseline = 0.0;
}
