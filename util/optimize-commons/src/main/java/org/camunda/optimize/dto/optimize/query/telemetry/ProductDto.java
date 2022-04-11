/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.telemetry;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import static org.camunda.optimize.service.metadata.Version.RAW_VERSION;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Builder
@Data
public class ProductDto implements OptimizeDto {
  @NonNull
  @Builder.Default
  private String name = "Camunda Optimize";
  @NonNull
  @Builder.Default
  private String version = RAW_VERSION;
  @NonNull
  @Builder.Default
  private String edition = "enterprise";
  @NonNull
  private InternalsDto internals;
}
