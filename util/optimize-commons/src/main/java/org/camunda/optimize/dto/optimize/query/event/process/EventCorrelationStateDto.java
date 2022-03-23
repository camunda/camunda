/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
public class EventCorrelationStateDto {
  private Map<MappedEventType, Set<String>> correlatedAsToFlowNodeInstanceIds = new EnumMap<>(MappedEventType.class);
}
