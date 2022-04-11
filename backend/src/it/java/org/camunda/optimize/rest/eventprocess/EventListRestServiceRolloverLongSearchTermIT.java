/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Comparator;

import static java.util.Comparator.naturalOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;

public class EventListRestServiceRolloverLongSearchTermIT extends AbstractEventRestServiceRolloverIT {

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
}
