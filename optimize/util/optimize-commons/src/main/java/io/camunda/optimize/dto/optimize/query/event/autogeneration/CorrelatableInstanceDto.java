/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class CorrelatableInstanceDto {
  private OffsetDateTime startDate;

  public abstract String getSourceIdentifier();

  public abstract String getCorrelationValueForEventSource(
      EventSourceEntryDto<?> eventSourceEntryDto);
}
