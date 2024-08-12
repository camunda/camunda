/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventImportSourceDto {

  private OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp;
  private OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp;

  private OffsetDateTime lastImportedEventTimestamp;
  private OffsetDateTime lastImportExecutionTimestamp;

  private EventSourceType eventImportSourceType;
  // If the source type is 'Camunda', there should be a single config in this list. If 'External',
  // there can be multiple
  private List<EventSourceConfigDto> eventSourceConfigurations;

  public static final class Fields {

    public static final String firstEventForSourceAtTimeOfPublishTimestamp =
        "firstEventForSourceAtTimeOfPublishTimestamp";
    public static final String lastEventForSourceAtTimeOfPublishTimestamp =
        "lastEventForSourceAtTimeOfPublishTimestamp";
    public static final String lastImportedEventTimestamp = "lastImportedEventTimestamp";
    public static final String lastImportExecutionTimestamp = "lastImportExecutionTimestamp";
    public static final String eventImportSourceType = "eventImportSourceType";
    public static final String eventSourceConfigurations = "eventSourceConfigurations";
  }
}
