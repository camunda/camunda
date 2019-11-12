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
import org.camunda.optimize.dto.optimize.persistence.EventBasedProcessDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class EventBasedProcessRestServiceIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_XML_WITH_NAME = "bpmn/simple_withName.bpmn";
  private static final String PROCESS_DEFINITION_XML_NO_NAME = "bpmn/simple_woName.bpmn";

  @Test
  public void createEventBasedProcessWithoutAuthorization() throws IOException {
    // when
    Response response = createCreateEventProcessDtoRequest(createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME))
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void createEventBasedProcess() throws IOException {
    // when
    Response response = createCreateEventProcessDtoRequest(createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(200);
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
    EventBasedProcessDto expected = createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME);
    String expectedId = createCreateEventProcessDtoRequest(expected).execute(IdDto.class, 200).getId();

    // when
    EventBasedProcessDto actual = createGetEventBasedProcessRequest(expectedId).execute(
      EventBasedProcessDto.class,
      200
    );

    // then the report is returned with expect
    assertThat(actual.getId()).isEqualTo(expectedId);
    assertThat(actual).isEqualToIgnoringGivenFields(expected, "id");
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
    EventBasedProcessDto firstExpectedDto = createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME);
    String firstExpectedId = createCreateEventProcessDtoRequest(firstExpectedDto).execute(IdDto.class, 200).getId();
    EventBasedProcessDto secondExpectedDto = createEventBasedProcessDto(PROCESS_DEFINITION_XML_NO_NAME);
    String secondExpectedId = createCreateEventProcessDtoRequest(secondExpectedDto).execute(IdDto.class, 200).getId();

    // when
    List<EventBasedProcessDto> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllEventBasedProcessRequests()
      .executeAndReturnList(EventBasedProcessDto.class, 200);

    // then the response contains expected processes with xml omitted
    assertThat(response).extracting("id", "name", "xml")
      .containsExactlyInAnyOrder(
        tuple(firstExpectedId, firstExpectedDto.getName(), null),
        tuple(secondExpectedId, secondExpectedDto.getName(), null)
      );
  }

  @Test
  public void updateEventBasedProcessWithoutAuthorization() throws IOException {
    // when
    EventBasedProcessDto updateDto = createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME);
    Response response = createUpdateEventBasedProcessRequest(UUID.randomUUID().toString(), updateDto)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void updateEventBasedProcess() throws IOException {
    // given
    String storedEventBasedProcessId = createCreateEventProcessDtoRequest(createEventBasedProcessDto(
      PROCESS_DEFINITION_XML_NO_NAME)).execute(IdDto.class, 200).getId();

    // when
    EventBasedProcessDto updateDto = createEventBasedProcessDto(
      "new process name",
      PROCESS_DEFINITION_XML_WITH_NAME
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
      createEventBasedProcessDto(PROCESS_DEFINITION_XML_WITH_NAME)
    ).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(404);
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
      PROCESS_DEFINITION_XML_NO_NAME)).execute(IdDto.class, 200).getId();

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

  private EventBasedProcessDto createEventBasedProcessDto(final String name,
                                                          final String xmlPath) throws IOException {
    EventBasedProcessDto eventBasedProcessDto = new EventBasedProcessDto();
    eventBasedProcessDto.setName(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)));
    eventBasedProcessDto.setXml(
      IOUtils.toString(
        getClass().getClassLoader().getResourceAsStream(xmlPath),
        "UTF-8"
      ));
    return eventBasedProcessDto;
  }

}
