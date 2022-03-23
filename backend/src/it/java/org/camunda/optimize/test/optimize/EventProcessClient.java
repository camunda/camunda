/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleResponseDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

@AllArgsConstructor
@Slf4j
public class EventProcessClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public boolean getIsEventBasedProcessEnabled() {
    return getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .execute(Boolean.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createCreateEventProcessMappingRequest(final EventProcessMappingDto eventProcessMappingDto) {
    return createCreateEventProcessMappingRequest(toEventProcessMappingCreateRequestDto(eventProcessMappingDto));
  }

  public OptimizeRequestExecutor createCreateEventProcessMappingRequest(final EventProcessMappingCreateRequestDto createRequest) {
    return getRequestExecutor().buildCreateEventProcessMappingRequest(createRequest);
  }

  public String createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    return createCreateEventProcessMappingRequest(eventProcessMappingDto).execute(
      IdResponseDto.class,
      Response.Status.OK.getStatusCode()
    ).getId();
  }

  public String createEventProcessMapping(final EventProcessMappingCreateRequestDto processMappingCreateRequestDto) {
    return createCreateEventProcessMappingRequest(processMappingCreateRequestDto).execute(
      IdResponseDto.class,
      Response.Status.OK.getStatusCode()
    ).getId();
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRequest(eventProcessMappingId);
  }

  public EventProcessMappingResponseDto getEventProcessMapping(final String eventProcessMappingId) {
    return createGetEventProcessMappingRequest(eventProcessMappingId).execute(
      EventProcessMappingResponseDto.class,
      Response.Status.OK.getStatusCode()
    );
  }

  private OptimizeRequestExecutor createGetAllEventProcessMappingsRequest() {
    return getRequestExecutor().buildGetAllEventProcessMappingsRequests();
  }

  private OptimizeRequestExecutor createGetAllEventProcessMappingsRequest(final String userId, final String pw) {
    return getRequestExecutor().withUserAuthentication(userId, pw).buildGetAllEventProcessMappingsRequests();
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings() {
    return createGetAllEventProcessMappingsRequest()
      .executeAndReturnList(EventProcessMappingDto.class, Response.Status.OK.getStatusCode());
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings(final String userId, final String pw) {
    return createGetAllEventProcessMappingsRequest(userId, pw)
      .executeAndReturnList(EventProcessMappingDto.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createUpdateEventProcessMappingRequest(final String eventProcessMappingId,
                                                                        final EventProcessMappingDto eventProcessMappingDto) {
    return getRequestExecutor()
      .buildUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String eventProcessMappingId, final EventProcessMappingDto updateDto) {
    createUpdateEventProcessMappingRequest(
      eventProcessMappingId,
      updateDto
    ).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    createPublishEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  @SneakyThrows
  public void waitForEventProcessPublish(final String eventProcessMappingId) {
    EventProcessMappingResponseDto eventProcessMapping;
    do {
      eventProcessMapping = getEventProcessMapping(eventProcessMappingId);
      log.info(
        "Event Process {} publish state: {} and progress: {}",
        eventProcessMapping.getId(),
        eventProcessMapping.getState(),
        eventProcessMapping.getPublishingProgress()
      );
      Thread.sleep(1000L);
    } while (!EventProcessState.PUBLISHED.equals(eventProcessMapping.getState()));
  }

  public OptimizeRequestExecutor createCancelPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor()
      .buildCancelPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void cancelPublishEventProcessMapping(final String eventProcessMappingId) {
    createCancelPublishEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createDeleteEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildDeleteEventProcessMappingRequest(eventProcessMappingId);
  }

  public OptimizeRequestExecutor createBulkDeleteEventProcessMappingsRequest(final List<String> eventProcessMappingIds) {
    return getRequestExecutor().bulkDeleteEventProcessMappingsRequest(eventProcessMappingIds);
  }

  public ConflictResponseDto getDeleteConflictsForEventProcessMapping(String eventProcessMappingId) {
    return createGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId)
      .execute(ConflictResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createGetDeleteConflictsForEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId);
  }

  public boolean eventProcessMappingRequestBulkDeleteHasConflicts(final List<String> eventBasedProcessIds) {
    return getRequestExecutor().buildCheckBulkDeleteConflictsForEventProcessMappingRequest(eventBasedProcessIds)
      .execute(Boolean.class, Response.Status.OK.getStatusCode());
  }

  public void deleteEventProcessMapping(final String eventProcessMappingId) {
    createDeleteEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public EventProcessMappingDto buildEventProcessMappingDto(final String xml) {
    return buildEventProcessMappingDto(null, xml);
  }

  private EventProcessMappingDto buildEventProcessMappingDto(final String name, final String xml) {
    return buildEventProcessMappingDtoWithMappingsAndExternalEventSource(null, name, xml);
  }

  public List<EventProcessRoleResponseDto> getEventProcessMappingRoles(final String eventProcessMappingId) {
    return createGetEventProcessMappingRolesRequest(eventProcessMappingId)
      .execute(new TypeReference<List<EventProcessRoleResponseDto>>() {
      });
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRolesRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRolesRequest(eventProcessMappingId);
  }

  public void updateEventProcessMappingRoles(final String eventProcessMappingId,
                                             final List<EventProcessRoleRequestDto<IdentityDto>> roleRestDtos) {
    createUpdateEventProcessMappingRolesRequest(eventProcessMappingId, roleRestDtos)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createUpdateEventProcessMappingRolesRequest(final String eventProcessMappingId,
                                                                             final List<EventProcessRoleRequestDto<IdentityDto>> roleRestDtos) {
    return getRequestExecutor().buildUpdateEventProcessRolesRequest(eventProcessMappingId, roleRestDtos);
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto,
    final String name,
    final String xml) {
    List<EventSourceEntryDto<? extends EventSourceConfigDto>> externalEventSource = new ArrayList<>();
    externalEventSource.add(createExternalEventAllGroupsSourceEntry());
    return buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      flowNodeEventMappingsDto,
      name,
      xml,
      externalEventSource
    );
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto, final String name, final String xml,
    final List<EventSourceEntryDto<?>> eventSources) {
    return EventProcessMappingDto.builder()
      .name(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)))
      .mappings(flowNodeEventMappingsDto)
      .eventSources(eventSources)
      .xml(xml)
      .build();
  }

  public Map<String, EventMappingDto> cleanupEventProcessMappings(final EventMappingCleanupRequestDto cleanupRequestDto) {
    return createCleanupEventProcessMappingsRequest(cleanupRequestDto)
      // @formatter:off
      .execute(new TypeReference<Map<String, EventMappingDto>>() {});
    // @formatter:on
  }

  public OptimizeRequestExecutor createCleanupEventProcessMappingsRequest(final EventMappingCleanupRequestDto cleanupRequestDto) {
    return getRequestExecutor().buildCleanupEventProcessMappingRequest(cleanupRequestDto);
  }

  public static ExternalEventSourceEntryDto createExternalEventAllGroupsSourceEntry() {
    return ExternalEventSourceEntryDto.builder()
      .configuration(ExternalEventSourceConfigDto.builder()
                       .eventScope(Collections.singletonList(EventScopeType.ALL))
                       .includeAllGroups(true)
                       .build())
      .build();
  }

  public static ExternalEventSourceEntryDto createExternalEventSourceEntryForGroup(final String group) {
    return ExternalEventSourceEntryDto.builder()
      .configuration(ExternalEventSourceConfigDto.builder()
                       .eventScope(Collections.singletonList(EventScopeType.ALL))
                       .includeAllGroups(false).group(group).build())
      .build();
  }

  public static CamundaEventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey) {
    return createSimpleCamundaEventSourceEntryWithTenant(processDefinitionKey, null);
  }

  public static CamundaEventSourceEntryDto createSimpleCamundaEventSourceEntryWithTenant(final String processDefinitionKey,
                                                                                         final String tenantId) {
    return createSimpleCamundaEventSourceEntry(processDefinitionKey, ALL_VERSIONS, tenantId);
  }

  public static CamundaEventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey,
                                                                               final String version) {
    return createSimpleCamundaEventSourceEntry(processDefinitionKey, version, null);
  }

  public static CamundaEventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey,
                                                                               final String version,
                                                                               final String tenantId) {
    return CamundaEventSourceEntryDto.builder()
      .configuration(CamundaEventSourceConfigDto.builder()
                       .eventScope(Collections.singletonList(EventScopeType.ALL))
                       .processDefinitionKey(processDefinitionKey)
                       .versions(ImmutableList.of(version))
                       .tenants(Lists.newArrayList(tenantId))
                       .tracedByBusinessKey(true)
                       .build())
      .build();
  }

  public static EventMappingDto createEventMappingsDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  public static EventTypeDto createMappedEventDto() {
    return EventTypeDto.builder()
      .group(IdGenerator.getNextId())
      .source(IdGenerator.getNextId())
      .eventName(IdGenerator.getNextId())
      .build();
  }

  public ProcessInstanceDto createEventInstanceWithEvents(List<CloudEventRequestDto> eventsToInclude) {
    final String definitionKey = IdGenerator.getNextId();
    final String definitionVersion = "1";
    return EventProcessInstanceDto.builder()
      .processInstanceId(IdGenerator.getNextId())
      .processDefinitionKey(definitionKey)
      .processDefinitionVersion(definitionVersion)
      .endDate(LocalDateUtil.getCurrentDateTime().minusMinutes(1L))
      .endDate(LocalDateUtil.getCurrentDateTime())
      .duration(60000L)
      .state(ProcessInstanceConstants.COMPLETED_STATE)
      .variables(Collections.emptyList())
      .incidents(Collections.emptyList())
      .flowNodeInstances(
        eventsToInclude.stream().map(
          ingestedEvent ->
            new FlowNodeInstanceDto(
              definitionKey,
              definitionVersion,
              null,
              ingestedEvent.getTraceid(),
              IdGenerator.getNextId(),
              "startEvent",
              ingestedEvent.getId()
              )
              .setStartDate(LocalDateUtil.getCurrentDateTime().minusSeconds(30L))
              .setEndDate(LocalDateUtil.getCurrentDateTime().minusSeconds(10L))
              .setTotalDurationInMs(0L)
        ).collect(Collectors.toList())
      )
      .build();
  }

  private EventProcessMappingCreateRequestDto toEventProcessMappingCreateRequestDto(final EventProcessMappingDto eventProcessMappingDto) {
    return EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
      .name(eventProcessMappingDto.getName())
      .eventSources(eventProcessMappingDto.getEventSources())
      .mappings(eventProcessMappingDto.getMappings())
      .xml(eventProcessMappingDto.getXml())
      .build();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }

}
