/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class EventCountResponseDto {

  private String group;
  @NonNull private String source;
  @NonNull private String eventName;
  private String eventLabel;
  private Long count;
  @Builder.Default private boolean suggested = false;

  public static final class Fields {

    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String eventLabel = "eventLabel";
    public static final String count = "count";
    public static final String suggested = "suggested";
  }
}
