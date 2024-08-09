/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import java.util.List;

public interface EventSequenceCountWriter {

  void updateEventSequenceCountsWithAdjustments(
      final List<EventSequenceCountDto> eventSequenceCountDtos);

  default String getIndexName(final String indexKey) {
    return EventSequenceCountIndex.constructIndexName(indexKey);
  }
}
