/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CamundaActivityEventReaderOS implements CamundaActivityEventReader {

  @Override
  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(
      final String definitionKey, final Long eventTimestamp, final int limit) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(
      final String definitionKey, final Long eventTimestamp) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionBetween(
      final String definitionKey,
      final Long startTimestamp,
      final Long endTimestamp,
      final int limit) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public Set<String> getIndexSuffixesForCurrentActivityIndices() {
    log.debug("Functionality not implemented for OpenSearch");
    return new HashSet<>();
  }
}
