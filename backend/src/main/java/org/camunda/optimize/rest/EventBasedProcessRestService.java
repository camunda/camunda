/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleResponseDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.EventProcessRoleService;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.events.EventMappingCleanupService;
import org.camunda.optimize.service.exceptions.EventProcessManagementForbiddenException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.EventProcessAuthorizationService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Path("/eventBasedProcess")
@Component
public class EventBasedProcessRestService {

  private final EventProcessService eventProcessService;
  private final EventProcessRoleService eventProcessRoleService;
  private final EventMappingCleanupService eventMappingCleanupService;
  private final EventProcessAuthorizationService authenticationService;
  private final SessionService sessionService;
  private final DefinitionService definitionService;
  private final AbstractIdentityService identityService;

  @GET
  @Path("/isEnabled")
  @Produces(MediaType.APPLICATION_JSON)
  public boolean getIsEnabled(@Context ContainerRequestContext requestContext) {
    return isUserGrantedEventProcessManagementAccess(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EventProcessMappingResponseDto getEventProcessMapping(@PathParam("id") final String eventProcessId,
                                                               @Context ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return mapMappingDtoToRestDto(userId, eventProcessService.getEventProcessMapping(userId, eventProcessId));
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessMappingResponseDto> getAllEventProcessMappingsOmitXml(
    @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return eventProcessService.getAllEventProcessMappingsOmitXml(userId)
      .stream()
      .map(mappingRestDto -> mapMappingDtoToRestDto(userId, mappingRestDto))
      .collect(toList());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createEventProcessMapping(@Valid final EventProcessMappingCreateRequestDto createRequestDto,
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
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    eventProcessService.publishEventProcessMapping(userId, eventProcessId);
  }

  @POST
  @Path("/{id}/_cancelPublish")
  @Produces(MediaType.APPLICATION_JSON)
  public void cancelEventProcessPublish(@PathParam("id") final String eventProcessId,
                                        @Context final ContainerRequestContext requestContext) {
    validateAccessToEventProcessManagement(
      sessionService.getRequestUserOrFailNotAuthorized(requestContext)
    );
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    eventProcessService.cancelPublish(userId, eventProcessId);
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
  public List<EventProcessRoleResponseDto> getRoles(@PathParam("id") final String eventProcessId,
                                                    @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventProcessRoleService.getRoles(eventProcessId)
      .stream()
      .filter(eventRole -> identityService.isUserAuthorizedToAccessIdentity(userId, eventRole.getIdentity()))
      .map(this::mapToEventProcessRoleRestDto)
      .collect(toList());
  }

  @PUT
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRoles(@PathParam("id") final String eventProcessId,
                          @NotNull final List<EventProcessRoleRequestDto<IdentityDto>> rolesDtoRequest,
                          @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    final List<EventProcessRoleRequestDto<IdentityDto>> eventRoleDtos = rolesDtoRequest.stream()
      .map(roleDto -> resolveToEventProcessRoleDto(userId, roleDto))
      .peek(roleDto -> identityService.validateUserAuthorizedToAccessRoleOrFail(userId, roleDto.getIdentity()))
      .collect(toList());
    eventProcessRoleService.updateRoles(eventProcessId, eventRoleDtos, userId);
  }

  @POST
  @Path("/_mappingCleanup")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, EventMappingDto> doMappingCleanup(@Context final ContainerRequestContext requestContext,
                                                       @Valid @NotNull @RequestBody final EventMappingCleanupRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAccessToEventProcessManagement(userId);
    return eventMappingCleanupService.doMappingCleanup(userId, requestDto);
  }

  @POST
  @Path("/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean checkEventBasedProcessesHaveConflicts(@Context ContainerRequestContext requestContext,
                                                       @NotNull @RequestBody List<String> eventBasedProcessIds) {
    validateAccessToEventProcessManagement(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    return eventProcessService.hasDeleteConflicts(eventBasedProcessIds);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteEventProcessMappings(@Context ContainerRequestContext requestContext,
                                             @NotNull @RequestBody List<String> eventBasedProcessIds) {
    validateAccessToEventProcessManagement(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    eventProcessService.bulkDeleteEventProcessMappings(eventBasedProcessIds);
  }

  private EventProcessRoleRequestDto<IdentityDto> resolveToEventProcessRoleDto(final String userId,
                                                                               final EventProcessRoleRequestDto<IdentityDto> eventProcessRoleRestDto) {
    IdentityDto simpleIdentityDto = eventProcessRoleRestDto.getIdentity();
    if (simpleIdentityDto.getType() == null) {
      final String identityId = simpleIdentityDto.getId();
      simpleIdentityDto = identityService.getIdentityWithMetadataForIdAsUser(userId, identityId)
        .orElseThrow(() -> new OptimizeUserOrGroupIdNotFoundException(
          String.format("No user or group with ID %s exists in Optimize.", identityId)
        ))
        .toIdentityDto();
    }
    return new EventProcessRoleRequestDto<>(simpleIdentityDto);
  }

  private EventProcessRoleResponseDto mapToEventProcessRoleRestDto(final EventProcessRoleRequestDto<IdentityDto> roleDto) {
    return identityService.getIdentityWithMetadataForId(roleDto.getIdentity().getId())
      .map(EventProcessRoleResponseDto::new)
      .orElseThrow(() -> new OptimizeRuntimeException(
        "Could not map EventProcessRoleDto to EventProcessRoleRestDto, identity ["
          + roleDto.getIdentity().toString() + "] could not be found."
      ));
  }

  private void validateAccessToEventProcessManagement(final String userId) {
    if (!isUserGrantedEventProcessManagementAccess(userId)) {
      throw new EventProcessManagementForbiddenException(userId);
    }
  }

  private boolean isUserGrantedEventProcessManagementAccess(final String userId) {
    return authenticationService.hasEventProcessManagementAccess(userId);
  }

  private EventProcessMappingResponseDto mapMappingDtoToRestDto(final String userId, final EventProcessMappingDto dto) {
    final String lastModifierName = identityService.getIdentityNameById(dto.getLastModifier())
      .orElse(dto.getLastModifier());
    return EventProcessMappingResponseDto.from(
      dto,
      lastModifierName,
      mapSourceEntriesToRestDtos(
        userId,
        dto.getEventSources()
      )
    );
  }

  private List<EventSourceEntryDto<?>> mapSourceEntriesToRestDtos(final String userId,
                                                                  final List<EventSourceEntryDto<?>> eventSourceDtos) {
    return eventSourceDtos.stream()
      .peek(eventSource -> {
        if (eventSource instanceof CamundaEventSourceEntryDto) {
          final CamundaEventSourceConfigDto sourceConfig = (CamundaEventSourceConfigDto) eventSource.getConfiguration();
          sourceConfig.setProcessDefinitionName(getDefinitionName(userId, sourceConfig.getProcessDefinitionKey()));
        }
      })
      .collect(toList());
  }

  private String getDefinitionName(final String userId, final String eventSource) {
    return definitionService.getDefinitionWithAvailableTenants(DefinitionType.PROCESS, eventSource, userId)
      .map(DefinitionResponseDto::getName)
      .orElse(eventSource);
  }
}
