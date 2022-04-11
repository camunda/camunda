/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class LicenseInformationResponseDto implements OptimizeDto, Serializable {

  private String customerId;
  private OffsetDateTime validUntil;
  private boolean isUnlimited;
}
