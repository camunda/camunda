/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.service.db.reader.ExternalEventReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ExternalEventReaderOS implements ExternalEventReader {

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<EventDto> getEventsIngestedAfterForGroups(final Long ingestTimestamp, final int limit, final List<String> groups) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<EventDto> getEventsIngestedAtForGroups(final Long ingestTimestamp, final List<String> groups) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForGroups(final List<String> groups) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Page<DeletableEventDto> getEventsForRequest(final EventSearchRequestDto eventSearchRequestDto) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    //todo will be handled in the OPT-7230
    return null;
  }

}
