/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventBasedProcessDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.MappedEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class EventBasedProcessRestServiceIT extends AbstractIT {

  private static final String FULL_PROCESS_DEFINITION_XML = "bpmn/leadQualification.bpmn";
  private static final String VALID_SCRIPT_TASK_ID = "ScriptTask_1";
  private static final String VALID_USER_TASK_ID = "UserTask_1d75hsy";
  private static final String VALID_SERVICE_TASK_ID = "ServiceTask_0j2w5af";

  @Test
  public void createEventBasedProcessWithoutAuthorization() throws IOException {
    // when
    Response response = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML))
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void createEventBasedProcess() throws IOException {
    // when
    Response response = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void createEventBasedProcessWithEventMappingCombinations() throws IOException {
    // given event mappings with IDs existing in XML
    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(VALID_SCRIPT_TASK_ID, createEventMappingsDto(createMappedEventDto(), createMappedEventDto()));
    eventMappings.put(VALID_USER_TASK_ID, createEventMappingsDto(createMappedEventDto(), null));
    eventMappings.put(VALID_SERVICE_TASK_ID, createEventMappingsDto(null, createMappedEventDto()));
    EventBasedProcessDto eventBasedProcessDto = createEventBasedProcessDtoWithMappings(
      eventMappings,
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );

    // when
    Response response = createCreateEventProcessDtoRequest(eventBasedProcessDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void createEventBasedProcessWithEventMappingIdNotExistInXml() throws IOException {
    // given event mappings with ID does not exist in XML
    EventBasedProcessDto eventBasedProcessDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );

    // when
    Response response = createCreateEventProcessDtoRequest(eventBasedProcessDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void createEventBasedProcessWithEventMappingsAndXmlNotPresent() throws IOException {
    // given event mappings but no XML provided
    EventBasedProcessDto eventBasedProcessDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      null
    );

    // when
    Response response = createCreateEventProcessDtoRequest(eventBasedProcessDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @ParameterizedTest(name = "Invalid mapped event: {0}")
  @MethodSource("createInvalidMappedEventDtos")
  public void createEventBasedProcessWithInvalidEventMappings(MappedEventDto invalidMappedEventDto) throws IOException {
    // given event mappings but mapped events have fields missing
    invalidMappedEventDto.setGroup(null);
    EventBasedProcessDto eventBasedProcessDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(invalidMappedEventDto, createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );

    // when
    Response response = createCreateEventProcessDtoRequest(eventBasedProcessDto)
      .execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void getEventBasedProcessWithoutAuthorization() {
    // when
    Response response = createGetEventBasedProcessRequest(UUID.randomUUID().toString())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void getEventBasedProcessWithId() throws IOException {
    // given
    EventBasedProcessDto eventBasedProcessDto =  createEventBasedProcessDtoWithMappings(
      Collections.singletonMap(VALID_SERVICE_TASK_ID, createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );
    String expectedId = createCreateEventProcessDtoRequest(eventBasedProcessDto).execute(IdDto.class, 200).getId();

    // when
    EventBasedProcessDto actual = createGetEventBasedProcessRequest(expectedId).execute(
      EventBasedProcessDto.class,
      200
    );

    // then the report is returned with expect
    assertThat(actual.getId()).isEqualTo(expectedId);
    assertThat(actual).isEqualToIgnoringGivenFields(eventBasedProcessDto, "id");
  }

  @Test
  public void getEventBasedProcessWithIdNotExists() {
    // when
    Response response = createGetEventBasedProcessRequest(UUID.randomUUID().toString()).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void getAllEventBasedProcessWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllEventBasedProcessRequests()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void getAllEventBasedProcess() throws IOException {
    // given
    EventBasedProcessDto firstExpectedDto = createEventBasedProcessDto(FULL_PROCESS_DEFINITION_XML);
    String firstExpectedId = createCreateEventProcessDtoRequest(firstExpectedDto).execute(IdDto.class, 200).getId();
    EventBasedProcessDto secondExpectedDto = createEventBasedProcessDto(FULL_PROCESS_DEFINITION_XML);
    String secondExpectedId = createCreateEventProcessDtoRequest(secondExpectedDto).execute(IdDto.class, 200).getId();

    // when
    List<EventBasedProcessDto> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllEventBasedProcessRequests()
      .executeAndReturnList(EventBasedProcessDto.class, 200);

    // then the response contains expected processes with xml omitted
    assertThat(response).extracting("id", "name", "xml", "mappings")
      .containsExactlyInAnyOrder(
        tuple(firstExpectedId, firstExpectedDto.getName(), null, null),
        tuple(secondExpectedId, secondExpectedDto.getName(), null, null)
      );
  }

  @Test
  public void updateEventBasedProcessWithoutAuthorization() throws IOException {
    // when
    EventBasedProcessDto updateDto = createEventBasedProcessDto(FULL_PROCESS_DEFINITION_XML);
    Response response = createUpdateEventBasedProcessRequest(UUID.randomUUID().toString(), updateDto)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void updateEventBasedProcessWithMappingsAdded() throws IOException {
    // given
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML)).execute(IdDto.class, 200).getId();

    // when
    EventBasedProcessDto updateDto =  createEventBasedProcessDtoWithMappings(
      Collections.singletonMap(VALID_SERVICE_TASK_ID, createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "new process name",
      FULL_PROCESS_DEFINITION_XML
    );
    Response response = createUpdateEventBasedProcessRequest(storedEventBasedProcessId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(204);

    // then the fields have been updated
    EventBasedProcessDto storedDto = createGetEventBasedProcessRequest(storedEventBasedProcessId)
      .execute(EventBasedProcessDto.class, 200);
    assertThat(storedDto)
      .isEqualToIgnoringGivenFields(updateDto, "id")
      .extracting("id").isEqualTo(storedEventBasedProcessId);
  }

  @Test
  public void updateEventBasedProcessWithIdNotExists() throws IOException {
    // when
    Response response = createUpdateEventBasedProcessRequest(
      UUID.randomUUID().toString(),
      createEventBasedProcessDto(FULL_PROCESS_DEFINITION_XML)
    ).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void updateEventBasedProcessWithEventMappingIdNotExistInXml() throws IOException {
    // given
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML)).execute(IdDto.class, 200).getId();

    // when update event mappings with ID does not exist in XML
    EventBasedProcessDto updateDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );
    Response response = createUpdateEventBasedProcessRequest(storedEventBasedProcessId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @ParameterizedTest(name = "Invalid mapped event: {0}")
  @MethodSource("createInvalidMappedEventDtos")
  public void updateEventBasedProcessWithInvalidEventMappings(MappedEventDto invalidMappedEventDto) throws IOException {
    // given existing event based process
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML)).execute(IdDto.class, 200).getId();

    // when update event mappings with a mapped event with missing fields
    EventBasedProcessDto updateDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap(VALID_SERVICE_TASK_ID, createEventMappingsDto(invalidMappedEventDto, createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML
    );
    Response response = createUpdateEventBasedProcessRequest(storedEventBasedProcessId, updateDto).execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void updateEventBasedProcessWithEventMappingAndNoXmlPresent() throws IOException {
    // given
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      null)).execute(IdDto.class, 200).getId();

    // when update event mappings and no XML present
    EventBasedProcessDto updateDto = createEventBasedProcessDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      null
    );
    Response response = createUpdateEventBasedProcessRequest(storedEventBasedProcessId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void deleteEventBasedProcessWithoutAuthorization() {
    // when
    Response response = createDeleteEventBasedProcessRequest(UUID.randomUUID().toString())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void deleteEventBasedProcess() throws IOException {
    // given
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      FULL_PROCESS_DEFINITION_XML)).execute(IdDto.class, 200).getId();

    // when
    Response response = createDeleteEventBasedProcessRequest(storedEventBasedProcessId).execute();

    // then the delete response code is correct
    assertThat(response.getStatus()).isEqualTo(204);

    // then the process no longer exists
    Response getResponse = createGetEventBasedProcessRequest(storedEventBasedProcessId).execute();
    assertThat(getResponse.getStatus()).isEqualTo(404);
  }

  @Test
  public void deleteEventBasedProcessNotExists() {
    // when
    Response response = createDeleteEventBasedProcessRequest(UUID.randomUUID().toString()).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(404);
  }

  private OptimizeRequestExecutor createCreateEventProcessDtoRequest(final EventBasedProcessDto eventBasedProcessDto) {
    return embeddedOptimizeExtension.getRequestExecutor().buildCreateEventBasedProcessRequest(eventBasedProcessDto);
  }

  private OptimizeRequestExecutor createGetEventBasedProcessRequest(final String eventBasedProcessId) {
    return embeddedOptimizeExtension.getRequestExecutor().buildGetEventBasedProcessRequest(eventBasedProcessId);
  }

  private OptimizeRequestExecutor createUpdateEventBasedProcessRequest(final String eventBasedProcessId,
                                                                       final EventBasedProcessDto eventBasedProcessDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateEventBasedProcessRequest(eventBasedProcessId, eventBasedProcessDto);
  }

  private OptimizeRequestExecutor createDeleteEventBasedProcessRequest(final String eventBasedProcessId) {
    return embeddedOptimizeExtension.getRequestExecutor().buildDeleteEventBasedProcessRequest(eventBasedProcessId);
  }

  private EventBasedProcessDto createEventBasedProcessDto(final String xmlPath) throws IOException {
    return createEventBasedProcessDto(null, xmlPath);
  }

  private EventBasedProcessDto createEventBasedProcessDto(final String name, final String xmlPath) throws IOException {
    return createEventBasedProcessDtoWithMappings(null, name, xmlPath);
  }

  private EventBasedProcessDto createEventBasedProcessDtoWithMappings(final Map<String, EventMappingDto> flowNodeEventMappingsDto,
                                                                      final String name, final String xmlPath) throws
                                                                                                               IOException {
    EventBasedProcessDto eventBasedProcessDto = new EventBasedProcessDto();
    eventBasedProcessDto.setMappings(flowNodeEventMappingsDto);
    eventBasedProcessDto.setName(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)));
    String xml = xmlPath == null ? xmlPath : IOUtils.toString(getClass().getClassLoader().getResourceAsStream(xmlPath), "UTF-8");
    eventBasedProcessDto.setXml(xml);
    return eventBasedProcessDto;
  }

  private EventMappingDto createEventMappingsDto(MappedEventDto startEventDto, MappedEventDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private static Stream<MappedEventDto> createInvalidMappedEventDtos() {
    return Stream.of(
      MappedEventDto.builder().group(null).source(UUID.randomUUID().toString()).eventName(UUID.randomUUID().toString()).build(),
      MappedEventDto.builder().group(UUID.randomUUID().toString()).source(null).eventName(UUID.randomUUID().toString()).build(),
      MappedEventDto.builder().group(UUID.randomUUID().toString()).source(UUID.randomUUID().toString()).eventName(null).build()
    );
  }

  private static MappedEventDto createMappedEventDto() {
    return MappedEventDto.builder()
      .group(UUID.randomUUID().toString())
      .source(UUID.randomUUID().toString())
      .eventName(UUID.randomUUID().toString())
      .build();
  }

}
