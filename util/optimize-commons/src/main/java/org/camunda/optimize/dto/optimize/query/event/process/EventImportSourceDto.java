/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;

import java.time.OffsetDateTime;
import java.util.List;

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

  private EventSourceType eventImportSourceType;
  // If the source type is 'Camunda', there should be a single config in this list. If 'External', there can be multiple
  private List<EventSourceConfigDto> eventSourceConfigurations;

}
