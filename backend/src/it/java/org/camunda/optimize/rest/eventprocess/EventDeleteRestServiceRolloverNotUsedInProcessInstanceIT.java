/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDeleteRestServiceRolloverNotUsedInProcessInstanceIT extends AbstractEventRestServiceRolloverIT {

  @Test
  public void deleteRolledOverEvents_deleteSingleEventNotUsedInEventInstance() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<EventDto> savedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(savedEventsBeforeDelete.get(0).getId());

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThatEventsHaveBeenDeleted(savedEventsBeforeDelete, eventIdsToDelete);
  }
}
