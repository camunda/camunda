/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class CorrelatableInstanceDto {
  private OffsetDateTime startDate;

  public abstract String getSourceIdentifier();

  public abstract String getCorrelationValueForEventSource(EventSourceEntryDto<?> eventSourceEntryDto);
}
