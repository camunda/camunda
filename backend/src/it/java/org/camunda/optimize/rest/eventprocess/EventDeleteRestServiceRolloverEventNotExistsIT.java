/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDeleteRestServiceRolloverEventNotExistsIT extends AbstractEventRestServiceRolloverIT {

  @Test
  public void deleteRolledOverEvents_deleteSingleEventDoesNotExist() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<CloudEventRequestDto> instanceEvents = Arrays.asList(impostorSabotageNav, impostorMurderedMedBay);
    createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final List<EventDto> savedEventsBeforeDelete = getAllStoredEvents();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList("eventDoesNotExist"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllStoredEvents()).containsExactlyInAnyOrderElementsOf(savedEventsBeforeDelete);
  }

}
