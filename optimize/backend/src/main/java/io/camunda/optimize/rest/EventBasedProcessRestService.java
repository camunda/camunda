/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import io.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import io.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import io.camunda.optimize.dto.optimize.rest.EventProcessRoleResponseDto;
import io.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import io.camunda.optimize.service.EventProcessRoleService;
import io.camunda.optimize.service.EventProcessService;
import io.camunda.optimize.service.events.EventMappingCleanupService;
import io.camunda.optimize.service.exceptions.EventProcessManagementForbiddenException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.EventProcessAuthorizationService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@AllArgsConstructor
@Path("/eventBasedProcess")
@Component
public class EventBasedProcessRestService {

  private final EventProcessService eventProcessService;
  private final EventProcessRoleService eventProcessRoleService;
  private final EventMappingCleanupService eventMappingCleanupService;
  private final EventProcessAuthorizationService authenticationService;
  private final SessionService sessionService;
  private final AbstractIdentityService identityService;

  @GET
  @Path("/isEnabled")
  @Produces(MediaType.APPLICATION_JSON)
  public boolean getIsEnabled(@Context final ContainerRequestContext requestContext) {
    return isUserGrantedEventProcessManagementAccess(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EventProcessMappingResponseDto getEventProcessMapping(
      @PathParam("id") final String eventProcessId,
      @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    return mapMappingDtoToRestDto(eventProcessService.getEventProcessMapping(eventProcessId));
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessMappingResponseDto> getAllEventProcessMappingsOmitXml(
      @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return eventProcessService.getAllEventProcessMappingsOmitXml(userId).stream()
        .map(this::mapMappingDtoToRestDto)
        .toList();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createEventProcessMapping(
      @Valid final EventProcessMappingCreateRequestDto createRequestDto,
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventProcessService.createEventProcessMapping(userId, createRequestDto);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateEventProcessMapping(
      @PathParam("id") final String eventProcessId,
      @Context final ContainerRequestContext requestContext,
      @Valid final EventProcessMappingRequestDto updateRequestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    eventProcessService.updateEventProcessMapping(userId, eventProcessId, updateRequestDto);
  }

  @POST
  @Path("/{id}/_publish")
  @Produces(MediaType.APPLICATION_JSON)
  public void publishEventProcessMapping(
      @PathParam("id") final String eventProcessId,
      @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    eventProcessService.publishEventProcessMapping(eventProcessId);
  }

  @POST
  @Path("/{id}/_cancelPublish")
  @Produces(MediaType.APPLICATION_JSON)
  public void cancelEventProcessPublish(
      @PathParam("id") final String eventProcessId,
      @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    eventProcessService.cancelPublish(eventProcessId);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessRoleResponseDto> getRoles(
      @PathParam("id") final String eventProcessId,
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventProcessRoleService.getRoles(eventProcessId).stream()
        .filter(
            eventRole ->
                identityService.isUserAuthorizedToAccessIdentity(userId, eventRole.getIdentity()))
        .map(this::mapToEventProcessRoleRestDto)
        .toList();
  }

  @PUT
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRoles(
      @PathParam("id") final String eventProcessId,
      @NotNull final List<EventProcessRoleRequestDto<IdentityDto>> rolesDtoRequest,
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    final List<EventProcessRoleRequestDto<IdentityDto>> eventRoleDtos =
        rolesDtoRequest.stream()
            .map(roleDto -> resolveToEventProcessRoleDto(userId, roleDto))
            .peek(
                roleDto ->
                    identityService.validateUserAuthorizedToAccessRoleOrFail(
                        userId, roleDto.getIdentity()))
            .toList();
    eventProcessRoleService.updateRoles(eventProcessId, eventRoleDtos, userId);
  }

  @POST
  @Path("/_mappingCleanup")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, EventMappingDto> doMappingCleanup(
      @Context final ContainerRequestContext requestContext,
      @Valid @NotNull @RequestBody final EventMappingCleanupRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventMappingCleanupService.doMappingCleanup(userId, requestDto);
  }

  @POST
  @Path("/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean checkEventBasedProcessesHaveConflicts(
      @Context final ContainerRequestContext requestContext,
      @NotNull @RequestBody final List<String> eventBasedProcessIds) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    return eventProcessService.hasDeleteConflicts(eventBasedProcessIds);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteEventProcessMappings(
      @Context final ContainerRequestContext requestContext,
      @NotNull @RequestBody final List<String> eventBasedProcessIds) {
    validateAccessToEventProcessManagement(
        sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    eventProcessService.bulkDeleteEventProcessMappings(eventBasedProcessIds);
  }

  private EventProcessRoleRequestDto<IdentityDto> resolveToEventProcessRoleDto(
      final String userId, final EventProcessRoleRequestDto<IdentityDto> eventProcessRoleRestDto) {
    IdentityDto simpleIdentityDto = eventProcessRoleRestDto.getIdentity();
    if (simpleIdentityDto.getType() == null) {
      final String identityId = simpleIdentityDto.getId();
      simpleIdentityDto =
          identityService
              .getIdentityWithMetadataForIdAsUser(userId, identityId)
              .orElseThrow(
                  () ->
                      new OptimizeUserOrGroupIdNotFoundException(
                          String.format(
                              "No user or group with ID %s exists in Optimize.", identityId)))
              .toIdentityDto();
    }
    return new EventProcessRoleRequestDto<>(simpleIdentityDto);
  }

  private EventProcessRoleResponseDto mapToEventProcessRoleRestDto(
      final EventProcessRoleRequestDto<IdentityDto> roleDto) {
    return identityService
        .getIdentityWithMetadataForId(roleDto.getIdentity().getId())
        .map(EventProcessRoleResponseDto::new)
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "Could not map EventProcessRoleDto to EventProcessRoleRestDto, identity ["
                        + roleDto.getIdentity().toString()
                        + "] could not be found."));
  }

  private void validateAccessToEventProcessManagement(final String userId) {
    if (!isUserGrantedEventProcessManagementAccess(userId)) {
      throw new EventProcessManagementForbiddenException(userId);
    }
  }

  private boolean isUserGrantedEventProcessManagementAccess(final String userId) {
    return authenticationService.hasEventProcessManagementAccess(userId);
  }

  private EventProcessMappingResponseDto mapMappingDtoToRestDto(final EventProcessMappingDto dto) {
    final String lastModifierName =
        identityService.getIdentityNameById(dto.getLastModifier()).orElse(dto.getLastModifier());
    return EventProcessMappingResponseDto.from(dto, lastModifierName, dto.getEventSources());
  }
}
