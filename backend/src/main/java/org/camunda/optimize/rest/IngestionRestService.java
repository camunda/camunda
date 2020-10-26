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
import org.camunda.optimize.dto.optimize.query.event.process.EventResponseDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
@Path(IngestionRestService.INGESTION_PATH)
@Component
public class IngestionRestService {
  public static final String INGESTION_PATH = "/ingestion";
  public static final String EVENT_BATCH_SUB_PATH = "/event/batch";

  public static final String CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH = "application/cloudevents-batch+json";
  public static final String QUERY_PARAMETER_ACCESS_TOKEN = "access_token";
  public static final String BEARER_PREFIX = "Bearer ";

  private final ConfigurationService configurationService;
  private final ExternalEventService eventService;

  @POST
  @Path(EVENT_BATCH_SUB_PATH)
  @Consumes({CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH, MediaType.APPLICATION_JSON})
  @Produces(MediaType.APPLICATION_JSON)
  public void ingestCloudEvents(final @Context ContainerRequestContext requestContext,
                                final @NotNull @Valid @RequestBody ValidList<CloudEventRequestDto> cloudEventDtos) {
    validateAccessToken(requestContext);
    eventService.saveEventBatch(mapToEventDto(cloudEventDtos));
  }

  private List<EventResponseDto> mapToEventDto(final List<CloudEventRequestDto> cloudEventDtos) {
    return cloudEventDtos.stream()
      .map(cloudEventDto -> EventResponseDto.builder()
        .id(cloudEventDto.getId())
        .eventName(cloudEventDto.getType())
        .timestamp(
          cloudEventDto.getTime()
            .orElse(LocalDateUtil.getCurrentDateTime().toInstant())
            .toEpochMilli()
        )
        .traceId(cloudEventDto.getTraceid())
        .group(cloudEventDto.getGroup().orElse(null))
        .source(cloudEventDto.getSource())
        .data(cloudEventDto.getData())
        .build())
      .collect(Collectors.toList());
  }

  private void validateAccessToken(final ContainerRequestContext requestContext) {
    final MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    final String queryParameterAccessToken = queryParameters.getFirst(QUERY_PARAMETER_ACCESS_TOKEN);

    final String expectedAccessToken = getAccessToken();
    if (!expectedAccessToken.equals(extractAuthorizationHeaderToken(requestContext))
      && !expectedAccessToken.equals(queryParameterAccessToken)) {
      throw new NotAuthorizedException("Invalid or no ingestion api secret provided.");
    }
  }

  private String extractAuthorizationHeaderToken(ContainerRequestContext requestContext) {
    return Optional.ofNullable(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
      .map(providedValue -> {
        if (providedValue.startsWith(BEARER_PREFIX)) {
          return providedValue.replaceFirst(BEARER_PREFIX, "");
        }
        return providedValue;
      }).orElse(null);
  }

  private String getAccessToken() {
    return configurationService.getEventBasedProcessConfiguration().getEventIngestion().getAccessToken();
  }

  @Data
  private static class ValidList<E> implements List<E> {
    @Delegate
    private List<E> list = new ArrayList<>();
  }

}
