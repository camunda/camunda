/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class EventBasedProcessQueryPerformanceTest extends AbstractQueryPerformanceTest {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .setAuthorizedUserIds(Collections.singletonList(DEFAULT_USER));
  }

  @Test
  public void testQueryPerformance_getEventBasedProcesses() {
    // given
    final int numberOfEntities = getNumberOfEntities();

    addEventProcessMappingsToOptimize(numberOfEntities);

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      numberOfEntities,
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .buildGetAllEventProcessMappingsRequests()
        .executeAndReturnList(EventProcessMappingResponseDto.class, Response.Status.OK.getStatusCode())
    );
  }

  private void addEventProcessMappingsToOptimize(final int numberOfDifferentEvents) {
    final Map<String, Object> mappingsById = IntStream.range(0, numberOfDifferentEvents)
      .mapToObj(index -> {
        final String mappingId = IdGenerator.getNextId();
        return EsEventProcessMappingDto.builder()
          .id(mappingId)
          .name("event based process name")
          .xml("some xml")
          .mappings(Collections.emptyList())
          .eventSources(Collections.emptyList())
          .lastModifier(DEFAULT_USER)
          .lastModified(OffsetDateTime.now())
          .roles(Collections.singletonList(new EventProcessRoleRequestDto(new IdentityDto(DEFAULT_USER, IdentityType.USER))))
          .build();
      })
      .collect(Collectors.toMap(EsEventProcessMappingDto::getId, mapping -> mapping));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new EventProcessMappingIndex().getIndexName(), mappingsById
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
