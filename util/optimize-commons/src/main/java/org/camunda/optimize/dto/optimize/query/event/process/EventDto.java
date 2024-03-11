/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@FieldNameConstants()
public class EventDto implements OptimizeDto, EventProcessEventDto {
  @NotBlank @EqualsAndHashCode.Include @ToString.Include private String id;
  @NotBlank @ToString.Include private String eventName;

  @NotNull
  @Min(0)
  @ToString.Include
  private Long timestamp;

  @ToString.Include private Long ingestionTimestamp;
  @NotBlank @ToString.Include private String traceId;
  @ToString.Include private String group;
  @ToString.Include private String source;
  private Object data;
}
