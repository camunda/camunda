/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class EventCorrelationStateDto {

  private Map<MappedEventType, Set<String>> correlatedAsToFlowNodeInstanceIds =
      new EnumMap<>(MappedEventType.class);

  public static final class Fields {

    public static final String correlatedAsToFlowNodeInstanceIds =
        "correlatedAsToFlowNodeInstanceIds";
  }
}
