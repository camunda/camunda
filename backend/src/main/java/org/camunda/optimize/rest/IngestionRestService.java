/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.EventService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Path(IngestionRestService.INGESTION_PATH)
@Component
public class IngestionRestService {
  public static final String INGESTION_PATH = "/ingestion";
  public static final String EVENT_SUB_PATH = "/event";
  public static final String EVENT_BATCH_SUB_PATH = EVENT_SUB_PATH + "/batch";

  public static String OPTIMIZE_API_SECRET_HEADER = "X-Optimize-API-Secret";

  private final ConfigurationService configurationService;
  private final EventService eventService;

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(EVENT_SUB_PATH)
  public void ingestEvent(final @Context ContainerRequestContext requestContext,
                          final @NotNull @Valid @RequestBody EventDto eventDto) {
    validateApiSecret(requestContext);
    eventService.saveEvent(eventDto);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(EVENT_BATCH_SUB_PATH)
  public void ingestEvents(final @Context ContainerRequestContext requestContext,
                           final @NotNull @Valid @RequestBody ValidList<EventDto> eventDtos) {
    validateApiSecret(requestContext);
    eventService.saveEventBatch(eventDtos);
  }

  private void validateApiSecret(final ContainerRequestContext requestContext) {
    if (!getApiSecret().equals(requestContext.getHeaderString(OPTIMIZE_API_SECRET_HEADER))) {
      throw new NotAuthorizedException("Invalid or no ingestion api secret provided.");
    }
  }

  private String getApiSecret() {
    return configurationService.getIngestionConfiguration().getApiSecret();
  }

  @Data
  private static class ValidList<E> implements List<E> {
    @Delegate
    private List<E> list = new ArrayList<>();
  }

}
