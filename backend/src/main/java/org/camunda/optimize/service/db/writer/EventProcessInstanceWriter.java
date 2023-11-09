/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;

import java.time.OffsetDateTime;
import java.util.List;

public interface EventProcessInstanceWriter {

   void setGatewayLookup(final List<EventProcessGatewayDto> gatewayLookup);

   void importProcessInstances(final List<EventProcessInstanceDto> eventProcessInstanceDtos);

   void deleteInstancesThatEndedBefore(final OffsetDateTime endDate);

   void deleteVariablesOfInstancesThatEndedBefore(final OffsetDateTime endDate);

   void deleteEventsWithIdsInFromAllInstances(final List<String> eventIdsToDelete);

}
