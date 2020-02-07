/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryRestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.EventProcessRoleService;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.EventProcessAuthenticationService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Path("/eventBasedProcess")
@Component
@Secured
public class EventBasedProcessRestService {

  private final EventProcessService eventProcessService;
  private final EventProcessRoleService eventProcessRoleService;
  private final EventProcessAuthenticationService authenticationService;
  private final SessionService sessionService;
  private final DefinitionService definitionService;
  private final IdentityService identityService;

  @GET
  @Path("/isEnabled")
  @Produces(MediaType.APPLICATION_JSON)
  public boolean getIsEnabled(@Context ContainerRequestContext requestContext) {
    return eventProcessService.isEventProcessImportEnabled()
      && isUserIsGrantedEventProcessManagementAccess(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EventProcessMappingRestDto getEventProcessMapping(@PathParam("id") final String eventProcessId,
                                                           @Context ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return mapMappingDtoToRestDto(userId, eventProcessService.getEventProcessMapping(eventProcessId));
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessMappingRestDto> getAllEventProcessMappingsOmitXml(
    @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return eventProcessService.getAllEventProcessMappingsOmitXml()
      .stream()
      .map(mappingRestDto -> mapMappingDtoToRestDto(userId, mappingRestDto))
      .collect(toList());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createEventProcessMapping(@Valid final EventProcessMappingRequestDto createRequestDto,
                                         @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventProcessService.createEventProcessMapping(userId, createRequestDto);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateEventProcessMapping(@PathParam("id") final String eventProcessId,
                                        @Context final ContainerRequestContext requestContext,
                                        @Valid final EventProcessMappingRequestDto updateRequestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    eventProcessService.updateEventProcessMapping(userId, eventProcessId, updateRequestDto);
  }

  @POST
  @Path("/{id}/_publish")
  @Produces(MediaType.APPLICATION_JSON)
  public void publishEventProcessMapping(@PathParam("id") final String eventProcessId,
                                         @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    eventProcessService.publishEventProcessMapping(eventProcessId);
  }

  @POST
  @Path("/{id}/_cancelPublish")
  @Produces(MediaType.APPLICATION_JSON)
  public void cancelEventProcessPublish(@PathParam("id") final String eventProcessId,
                                        @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    eventProcessService.cancelPublish(eventProcessId);
  }

  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                @PathParam("id") String eventProcessId) {
    validateAccessToEventProcessManagement(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    return eventProcessService.getDeleteConflictingItems(eventProcessId);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteEventProcess(@PathParam("id") final String eventProcessId,
                                 @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    final boolean wasFoundAndDeleted = eventProcessService.deleteEventProcessMapping(eventProcessId);

    if (!wasFoundAndDeleted) {
      final String errorMessage = String.format(
        "Could not delete event based process with id [%s]. Event based process does not exist." +
          "Maybe it was already deleted by someone else?",
        eventProcessId
      );
      throw new NotFoundException(errorMessage);
    }
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessRoleRestDto> getRoles(@PathParam("id") final String eventProcessId,
                                                @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    return eventProcessRoleService.getRoles(eventProcessId)
      .stream()
      .map(this::mapToEventProcessRoleRestDto)
      .collect(toList());
  }

  @PUT
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRoles(@PathParam("id") final String eventProcessId,
                          @NotNull final List<EventProcessRoleDto<IdentityDto>> rolesDtoRequest,
                          @Context final ContainerRequestContext requestContext) throws OptimizeConflictException {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    final List<EventProcessRoleDto<IdentityDto>> eventRoleDtos = rolesDtoRequest.stream()
      .map(this::resolveToEventProcessRoleDto)
      .collect(toList());
    eventProcessRoleService.updateRoles(eventProcessId, eventRoleDtos, userId);
  }

  private EventProcessRoleDto<IdentityDto> resolveToEventProcessRoleDto(final EventProcessRoleDto<IdentityDto> eventProcessRoleRestDto) {
    IdentityDto simpleIdentityDto = eventProcessRoleRestDto.getIdentity();
    if (simpleIdentityDto.getType() == null) {
      final String identityId = simpleIdentityDto.getId();
      simpleIdentityDto = identityService.getIdentityWithMetadataForId(identityId)
        .orElseThrow(() -> new OptimizeUserOrGroupIdNotFoundException(
          String.format("No user or group with ID %s exists in Optimize.", identityId)
        ))
        .toIdentityDto();
    }
    return new EventProcessRoleDto<>(simpleIdentityDto);
  }

  private EventProcessRoleRestDto mapToEventProcessRoleRestDto(final EventProcessRoleDto<IdentityDto> roleDto) {
    return identityService.resolveToIdentityWithMetadata(roleDto.getIdentity())
      .map(EventProcessRoleRestDto::new)
      .orElseThrow(() -> new OptimizeRuntimeException(
        "Could not map EventProcessRoleDto to EventProcessRoleRestDto, identity ["
          + roleDto.getIdentity().toString() + "] could not be found."
      ));
  }

  private void validateAccessToEventProcessManagement(final String userId) {
    if (!eventProcessService.isEventProcessImportEnabled()) {
      throw new ForbiddenException("The event process feature is not activated.");
    }
    if (!isUserIsGrantedEventProcessManagementAccess(userId)) {
      throw new ForbiddenException("The user " + userId + " is not authorized to use the event process api.");
    }
  }

  private boolean isUserIsGrantedEventProcessManagementAccess(final String userId) {
    return authenticationService.hasEventProcessManagementAccess(userId);
  }

  private EventProcessMappingRestDto mapMappingDtoToRestDto(final String userId, final EventProcessMappingDto dto) {
    EventProcessMappingRestDto restDto = EventProcessMappingRestDto.builder()
      .id(dto.getId())
      .lastModified(dto.getLastModified())
      .lastModifier(dto.getLastModifier())
      .mappings(dto.getMappings())
      .name(dto.getName())
      .state(dto.getState())
      .publishingProgress(dto.getPublishingProgress())
      .xml(dto.getXml())
      .eventSources(mapSourceEntryToRestDto(userId, dto.getEventSources()))
      .build();

    return restDto;
  }

  private List<EventSourceEntryRestDto> mapSourceEntryToRestDto(final String userId,
                                                                final List<EventSourceEntryDto> eventSourceDtos) {
    List<EventSourceEntryRestDto> sourceRestDtos = new ArrayList<>();

    for (EventSourceEntryDto sourceDto : eventSourceDtos) {
      final String definitionName = getDefinitionName(userId, sourceDto);

      EventSourceEntryRestDto sourceRestDto = EventSourceEntryRestDto.builder()
        .id(sourceDto.getId())
        .eventScope(sourceDto.getEventScope())
        .processDefinitionKey(sourceDto.getProcessDefinitionKey())
        .processDefinitionName(definitionName)
        .tracedByBusinessKey(sourceDto.getTracedByBusinessKey())
        .traceVariable(sourceDto.getTraceVariable())
        .versions(sourceDto.getVersions())
        .tenants(sourceDto.getTenants())
        .build();
      sourceRestDtos.add(sourceRestDto);
    }
    return sourceRestDtos;
  }

  private String getDefinitionName(final String userId, final EventSourceEntryDto eventSource) {
    return definitionService.getDefinition(DefinitionType.PROCESS, eventSource.getProcessDefinitionKey(), userId)
      .map(DefinitionWithTenantsDto::getName)
      .orElse(eventSource.getProcessDefinitionKey());
  }
}
