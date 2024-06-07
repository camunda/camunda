/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.repository.EventRepository;
import org.camunda.optimize.service.db.repository.EventRepository.TimeRangeRequest;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventReader {
  private final EventRepository eventRepository;
  DatabaseClient dbClient;

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(
      final String definitionKey, final Long eventTimestamp, final int limit) {
    log.debug(
        "Fetching camunda activity events for key [{}] and with timestamp after {}",
        definitionKey,
        eventTimestamp);

    return eventRepository.getPageOfEventsForDefinitionKeySortedByTimestamp(
        definitionKey, eventTimestamp, limit, TimeRangeRequest.AFTER);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(
      final String definitionKey, final Long eventTimestamp) {
    log.debug(
        "Fetching camunda activity events for key [{}] and with exact timestamp {}.",
        definitionKey,
        eventTimestamp);

    return eventRepository.getPageOfEventsForDefinitionKeySortedByTimestamp(
        definitionKey, eventTimestamp, MAX_RESPONSE_SIZE_LIMIT, TimeRangeRequest.AT);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionBetween(
      final String definitionKey,
      final Long startTimestamp,
      final Long endTimestamp,
      final int limit) {
    log.debug(
        "Fetching camunda activity events for key [{}] with timestamp between {} and {}",
        definitionKey,
        startTimestamp,
        endTimestamp);

    return eventRepository.getPageOfEventsForDefinitionKeySortedByTimestamp(
        definitionKey, Pair.of(startTimestamp, endTimestamp), limit, TimeRangeRequest.BETWEEN);
  }

  public Set<String> getIndexSuffixesForCurrentActivityIndices() {
    final Map<String, Set<String>> aliases;
    try {
      aliases = dbClient.getAliasesForIndexPattern(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*");
    } catch (final IOException e) {
      final String errorMessage =
          "Could not retrieve the definition keys for Camunda event imported definitions!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return aliases.values().stream()
        .flatMap(Collection::stream)
        .map(
            fullAliasName ->
                fullAliasName.substring(
                    fullAliasName.lastIndexOf(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)
                        + CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX.length()))
        .collect(Collectors.toSet());
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");
    return eventRepository.getMinAndMaxIngestedTimestampsForDefinition(processDefinitionKey);
  }
}
