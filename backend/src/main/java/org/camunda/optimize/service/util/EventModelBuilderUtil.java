/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;

import java.util.Arrays;

@Slf4j
@UtilityClass
public class EventModelBuilderUtil {

  private static final String EVENT = "event";
  public static final String DIVERGING_GATEWAY = "Diverging gateway";
  public static final String CONVERGING_GATEWAY = "Converging gateway";

  public static String generateNodeId(final EventTypeDto eventTypeDto) {
    return generateId(EVENT, eventTypeDto);
  }

  public static String generateId(String type, EventTypeDto eventTypeDto) {
    // The type prefix is necessary and should start with lower case so that the ID passes QName validation
    return String.join(
      "_",
      Arrays.asList(
        type,
        eventTypeDto.getGroup(),
        eventTypeDto.getSource(),
        eventTypeDto.getEventName()
      )
    );
  }

  public static String generateGatewayIdForSource(final EventSourceEntryDto eventSourceEntryDto,
                                                  final GatewayDirection gatewayDirection) {
    return String.join(
      "_",
      Arrays.asList(gatewayDirection.toString().toLowerCase(), eventSourceEntryDto.getProcessDefinitionKey())
    );
  }

  public static String generateGatewayIdForNode(final EventTypeDto eventTypeDto, GatewayDirection gatewayDirection) {
    return generateId(gatewayDirection.toString().toLowerCase(), eventTypeDto);
  }

}
