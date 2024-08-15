/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
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

  public static final class Fields {

    public static final String id = "id";
    public static final String eventName = "eventName";
    public static final String timestamp = "timestamp";
    public static final String ingestionTimestamp = "ingestionTimestamp";
    public static final String traceId = "traceId";
    public static final String group = "group";
    public static final String source = "source";
    public static final String data = "data";
  }
}
