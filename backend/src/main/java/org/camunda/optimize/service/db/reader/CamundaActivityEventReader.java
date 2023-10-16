/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CamundaActivityEventReader {

  String MIN_AGG = "min";
  String MAX_AGG = "max";

  List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(final String definitionKey,
                                                                           final Long eventTimestamp,
                                                                           final int limit);

  List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(final String definitionKey,
                                                                        final Long eventTimestamp);

  List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionBetween(
    final String definitionKey,
    final Long startTimestamp,
    final Long endTimestamp,
    final int limit);

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey);

  Set<String> getIndexSuffixesForCurrentActivityIndices();

}
