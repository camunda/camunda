/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class EventImportSourceDtoOld {
  private OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp;
  private OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp;
  private OffsetDateTime lastImportedEventTimestamp;
  private OffsetDateTime lastImportExecutionTimestamp;
  private EventSourceEntryDtoOld eventSource;
}
