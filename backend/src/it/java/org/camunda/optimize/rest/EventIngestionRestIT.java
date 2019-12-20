/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.RequestTargetHost;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.ValidationErrorResponseDto;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.id;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.timestamp;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.traceId;
import static org.camunda.optimize.jetty.MaxRequestSizeFilter.MESSAGE_NO_CONTENT_LENGTH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.OPTIMIZE_API_SECRET_HEADER;
import static org.camunda.optimize.rest.providers.BeanConstraintViolationExceptionHandler.THE_REQUEST_BODY_WAS_INVALID;

public class EventIngestionRestIT extends AbstractIT {

  @Test
  public void ingestSingleEvent() {
    // given
    final EventDto eventDto = eventClient.createEventDto();

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    assertEventDtosArePersisted(Collections.singletonList(eventDto));
  }

  @Test
  public void ingestSingleEventWithNoGroupOrSource() {
    // given
    final EventDto eventDto = eventClient.createEventDto();
    eventDto.setGroup(null);
    eventDto.setSource(null);

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    assertEventDtosArePersisted(Collections.singletonList(eventDto));
  }

  @Test
  public void ingestSingleEvent_rejectMandatoryPropertiesNull() {
    // given
    final EventDto eventDto = eventClient.createEventDto();
    eventDto.setId(null);
    eventDto.setEventName(null);
    eventDto.setTimestamp(null);
    eventDto.setTraceId(null);

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(ingestErrorResponse.getValidationErrors().size()).isEqualTo(4);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()))
      .contains(id, eventName, timestamp, traceId);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList())).doesNotContainNull();

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_rejectInvalidPropertyValues() {
    // given
    final EventDto eventDto = eventClient.createEventDto();
    eventDto.setId("  ");
    eventDto.setEventName("");
    eventDto.setTimestamp(-500L);
    eventDto.setTraceId("");

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(ingestErrorResponse.getValidationErrors().size()).isEqualTo(4);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()))
      .contains(id, eventName, timestamp, traceId);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList())).doesNotContainNull();

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_notAuthorized() {
    // given
    final EventDto eventDto = eventClient.createEventDto();

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, null)
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_UNAUTHORIZED);

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_customSecret() {
    // given
    final EventDto eventDto = eventClient.createEventDto();

    final String customSecret = "mySecret";
    embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().setApiSecret(customSecret);

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, customSecret)
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    assertEventDtosArePersisted(Collections.singletonList(eventDto));
  }

  @Test
  public void ingestEventBatch() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 10)
      .mapToObj(operand -> eventClient.createEventDto())
      .collect(toList());

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    assertEventDtosArePersisted(eventDtos);
  }

  @Test
  public void ingestEventBatch_notAuthorized() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> eventClient.createEventDto())
      .collect(toList());

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, "wroooong")
      .execute();

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(HttpStatus.SC_UNAUTHORIZED);

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestEventBatch_limitExceeded() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().setMaxBatchRequestBytes(1L);

    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> eventClient.createEventDto())
      .collect(toList());

    // when
    final ErrorResponseDto ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute(ErrorResponseDto.class, SC_REQUEST_ENTITY_TOO_LARGE);

    // then
    assertThat(ingestResponse.getErrorMessage()).contains("Request too large");

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestEventBatch_contentLengthHeaderMissing() throws IOException {
    // this is a custom apache client that does not send the content-length header
    try (final CloseableHttpClient httpClient = HttpClients.custom()
      .setHttpProcessor(HttpProcessorBuilder.create().addAll(new RequestTargetHost()).build())
      .build()) {
      final HttpPut httpPut = new HttpPut(
        IntegrationTestConfigurationUtil.getEmbeddedOptimizeRestApiEndpoint() + INGESTION_PATH + EVENT_BATCH_SUB_PATH
      );
      httpPut.addHeader(OPTIMIZE_API_SECRET_HEADER, getApiSecret());
      final CloseableHttpResponse response = httpClient.execute(httpPut);

      // then
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_LENGTH_REQUIRED);
      final ErrorResponseDto errorResponseDto = embeddedOptimizeExtension.getObjectMapper()
        .readValue(response.getEntity().getContent(), ErrorResponseDto.class);

      assertThat(errorResponseDto.getErrorMessage()).isEqualTo(MESSAGE_NO_CONTENT_LENGTH);

      assertEventDtosArePersisted(Collections.emptyList());
    }
  }

  @Test
  public void ingestEventBatch_rejectInvalidPropertyValues() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> eventClient.createEventDto())
      .collect(toList());

    final EventDto invalidEventDto1 = eventClient.createEventDto();
    invalidEventDto1.setId(null);
    eventDtos.add(invalidEventDto1);

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(ingestErrorResponse.getValidationErrors().size()).isEqualTo(1);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()))
      .contains("element[2]." + id);
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList())).doesNotContainNull();

    assertEventDtosArePersisted(Collections.emptyList());
  }

  private void assertEventDtosArePersisted(final List<EventDto> eventDtos) {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<EventDto> indexedEventDtos = eventClient.getAllStoredEvents();
    assertThat(indexedEventDtos).usingElementComparatorIgnoringFields(EventDto.Fields.ingestionTimestamp)
      .containsExactlyInAnyOrderElementsOf(eventDtos);
  }

  private String getApiSecret() {
    return embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().getApiSecret();
  }

}
