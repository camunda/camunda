/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;

@FieldNameConstants
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

  private EventSourceEntryDto eventSource;

  @JsonIgnore
  public String getId() {
    return eventSource.getId();
  }

}
