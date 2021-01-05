/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.webapp.es.reader.EventReader;
import org.camunda.operate.webapp.rest.dto.EventDto;
import org.camunda.operate.webapp.rest.dto.EventQueryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.webapp.rest.EventRestService.EVENTS_URL;

@Api(tags = {"Zeebe events"})
@SwaggerDefinition(tags = {
  @Tag(name = "Zeebe events", description = "Zeebe events")
})
@RestController
@RequestMapping(value = EVENTS_URL)
@Deprecated
public class EventRestService {

  public static final String EVENTS_URL = "/api/events";

  @Autowired
  private EventReader eventReader;

  @ApiOperation("List Zeebe events")
  @PostMapping
  public List<EventDto> getEvents(@RequestBody EventQueryDto eventQuery) {
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQuery);
    return EventDto.createFrom(eventEntities);
  }

}
