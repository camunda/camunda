/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder(toBuilder = true)
public class EventTypeDto implements OptimizeDto {

  private String group;
  private String source;
  @NotBlank private String eventName;
  @EqualsAndHashCode.Exclude private String eventLabel;

  public static final class Fields {

    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String eventLabel = "eventLabel";
  }
}
