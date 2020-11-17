/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;

import static java.util.Comparator.naturalOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;

public class EventListRestServiceRolloverIT extends AbstractEventProcessIT {

  private static final String TIMESTAMP = DeletableEventDto.Fields.timestamp;
  private static final String GROUP = DeletableEventDto.Fields.group;

  private CloudEventRequestDto impostorSabotageNav = createEventDtoWithProperties(
    "impostors",
    "navigationRoom",
    "sabotage",
    Instant.now()
  );

  private CloudEventRequestDto impostorMurderedMedBay = createEventDtoWithProperties(
    "impostors",
    "medBay",
    "murderedNormie",
    Instant.now().plusSeconds(1)
  );

  private CloudEventRequestDto normieTaskNav = createEventDtoWithProperties(
    "normie",
    "navigationRoom",
    "finishedTask",
    Instant.now().plusSeconds(2)
  );

  @BeforeEach
  public void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalEventIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
    embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_noSearchTerm() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        null,
        new SortRequestDto(GROUP, ASC),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then the results from all indices return sorted by parameters
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(ASC);
    assertThat(eventsPage.getTotal()).isEqualTo(3);
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(
        Comparator.comparing(DeletableEventDto::getGroup, Comparator.nullsFirst(naturalOrder()))
          .thenComparing(Comparator.comparing(DeletableEventDto::getTimestamp).reversed()))
      .hasSize(3)
      .extracting(DeletableEventDto::getId)
      .containsExactly(impostorMurderedMedBay.getId(), impostorSabotageNav.getId(), normieTaskNav.getId());
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_noSearchTerm_orSortParams() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        null,
        new SortRequestDto(null, null),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then the results from all indices return sorted by default property
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(3);
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(
        Comparator.comparing(DeletableEventDto::getTimestamp, Comparator.nullsFirst(naturalOrder())).reversed()
          .thenComparing(Comparator.comparing(DeletableEventDto::getTimestamp).reversed()))
      .hasSize(3)
      .extracting(DeletableEventDto::getId)
      .containsExactly(normieTaskNav.getId(), impostorMurderedMedBay.getId(), impostorSabotageNav.getId());
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_searchTermMatchingExactly() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        "impostors",
        new SortRequestDto(TIMESTAMP, DESC),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only the results matching the search term are included
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(2);
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(
        Comparator.comparing(DeletableEventDto::getTimestamp, Comparator.nullsFirst(naturalOrder())).reversed())
      .hasSize(2)
      .extracting(DeletableEventDto::getId)
      .containsExactly(impostorMurderedMedBay.getId(), impostorSabotageNav.getId());
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_searchTermMatchesContains() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        "impost",
        new SortRequestDto(TIMESTAMP, DESC),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only the results matching the search term are included
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(2);
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(
        Comparator.comparing(DeletableEventDto::getTimestamp, Comparator.nullsFirst(naturalOrder())).reversed())
      .hasSize(2)
      .extracting(DeletableEventDto::getId)
      .containsExactly(impostorMurderedMedBay.getId(), impostorSabotageNav.getId());
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_longSearchTermMatchesPrefix() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final String searchTerm = "navigationRo";

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        searchTerm,
        new SortRequestDto(TIMESTAMP, DESC),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only the results matching the search term are included
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(2);
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(
        Comparator.comparing(DeletableEventDto::getTimestamp, Comparator.nullsFirst(naturalOrder())).reversed())
      .hasSize(2)
      .extracting(DeletableEventDto::getId)
      .containsExactly(normieTaskNav.getId(), impostorSabotageNav.getId());
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_longSearchTermDoesNotMatchPrefix() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final String searchTerm = "vigationRoom";

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        searchTerm,
        new SortRequestDto(TIMESTAMP, DESC),
        new PaginationRequestDto(20, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then no results are returned
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isZero();
    assertThat(eventsPage.getResults()).isEmpty();
  }

  @Test
  public void getEventCountsWithRolledOverEventIndices_paginateThroughResults() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);

    // when I request the first page
    Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        null,
        new SortRequestDto(GROUP, ASC),
        new PaginationRequestDto(1, 0)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then the first page contains only the first event according to sort parameters
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(ASC);
    assertThat(eventsPage.getTotal()).isEqualTo(3);
    assertThat(eventsPage.getResults())
      .extracting(DeletableEventDto::getId)
      .containsExactly(impostorMurderedMedBay.getId());

    // when I request the second page
    eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        null,
        new SortRequestDto(GROUP, ASC),
        new PaginationRequestDto(1, 1)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then the second page contains only the second event according to sort parameters
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(ASC);
    assertThat(eventsPage.getTotal()).isEqualTo(3);
    assertThat(eventsPage.getResults())
      .extracting(DeletableEventDto::getId)
      .containsExactly(impostorSabotageNav.getId());

    // when I request the second page
    eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(new EventSearchRequestDto(
        null,
        new SortRequestDto(GROUP, ASC),
        new PaginationRequestDto(1, 2)
      ))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then the third page contains only the third event according to sort parameters
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(ASC);
    assertThat(eventsPage.getTotal()).isEqualTo(3);
    assertThat(eventsPage.getResults())
      .extracting(DeletableEventDto::getId)
      .containsExactly(normieTaskNav.getId());
  }

  private void ingestEventAndRolloverIndex(final CloudEventRequestDto cloudEventRequestDto) {
    eventClient.ingestEventBatch(Collections.singletonList(cloudEventRequestDto));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
  }

  private CloudEventRequestDto createEventDtoWithProperties(final String group,
                                                            final String source,
                                                            final String type,
                                                            final Instant timestamp) {
    return eventClient.createCloudEventDto()
      .toBuilder()
      .group(group)
      .source(source)
      .type(type)
      .time(timestamp)
      .build();
  }

}
