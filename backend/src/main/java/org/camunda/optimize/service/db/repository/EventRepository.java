/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;

public interface EventRepository {

  String MIN_AGG = "min";
  String MAX_AGG = "max";

  enum TimeRangeRequest {
    AT,
    BETWEEN,
    AFTER
  }

  default OffsetDateTime convertToOffsetDateTime(final Long eventTimestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimestamp), ZoneId.systemDefault());
  }

  void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds);

  List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode);

  default List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Long timestamp,
      final int limit,
      final TimeRangeRequest mode) {
    if (mode.equals(TimeRangeRequest.AT)) {
      return getPageOfEventsForDefinitionKeySortedByTimestamp(
          definitionKey, Pair.of(timestamp, timestamp), limit, mode);
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      return getPageOfEventsForDefinitionKeySortedByTimestamp(
          definitionKey, Pair.of(timestamp, Long.MAX_VALUE), limit, mode);
    } else {
      throw new IllegalArgumentException(
          "When using the between mode, you need to provide a pair of timestamps");
    }
  }

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey);
}
