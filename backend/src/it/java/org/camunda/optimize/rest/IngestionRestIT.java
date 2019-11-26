/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
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
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_LENGTH_REQUIRED;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.duration;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.id;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.timestamp;
import static org.camunda.optimize.dto.optimize.query.event.EventDto.Fields.traceId;
import static org.camunda.optimize.jetty.MaxRequestSizeFilter.MESSAGE_NO_CONTENT_LENGTH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.OPTIMIZE_API_SECRET_HEADER;
import static org.camunda.optimize.rest.providers.BeanConstraintViolationExceptionHandler.THE_REQUEST_BODY_WAS_INVALID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class IngestionRestIT extends AbstractIT {

  public static final Random RANDOM = new Random();

  @Test
  public void ingestSingleEvent() {
    // given
    final EventDto eventDto = createEventDto();

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute();

    // then
    assertThat(ingestResponse.getStatus(), is(204));

    assertEventDtosArePersisted(Collections.singletonList(eventDto));
  }

  @Test
  public void ingestSingleEvent_rejectMandatoryPropertiesNull() {
    // given
    final EventDto eventDto = createEventDto();
    eventDto.setId(null);
    eventDto.setEventName(null);
    eventDto.setTimestamp(null);
    eventDto.setTraceId(null);

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage(), is(THE_REQUEST_BODY_WAS_INVALID));
    assertThat(ingestErrorResponse.getValidationErrors().size(), is(4));
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()),
      containsInAnyOrder(
        id, eventName, timestamp, traceId
      )
    );
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList()),
      everyItem(is(notNullValue()))
    );

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_rejectInvalidPropertyValues() {
    // given
    final EventDto eventDto = createEventDto();
    eventDto.setId("  ");
    eventDto.setEventName("");
    eventDto.setTimestamp(-500L);
    eventDto.setTraceId("");
    eventDto.setDuration(-5L);

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage(), is(THE_REQUEST_BODY_WAS_INVALID));
    assertThat(ingestErrorResponse.getValidationErrors().size(), is(5));
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()),
      containsInAnyOrder(
        id, eventName, timestamp, traceId, duration
      )
    );
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList()),
      everyItem(is(notNullValue()))
    );

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_notAuthorized() {
    // given
    final EventDto eventDto = createEventDto();

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, null)
      .execute();

    // then
    assertThat(ingestResponse.getStatus(), is(401));

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestSingleEvent_customSecret() {
    // given
    final EventDto eventDto = createEventDto();

    final String customSecret = "mySecret";
    embeddedOptimizeExtension.getConfigurationService().getIngestionConfiguration()
      .setApiSecret(customSecret);

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestSingleEvent(eventDto, customSecret)
      .execute();

    // then
    assertThat(ingestResponse.getStatus(), is(204));

    assertEventDtosArePersisted(Collections.singletonList(eventDto));
  }

  @Test
  public void ingestEventBatch() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 10)
      .mapToObj(operand -> createEventDto())
      .collect(toList());

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute();

    // then
    assertThat(ingestResponse.getStatus(), is(204));

    assertEventDtosArePersisted(eventDtos);
  }

  @Test
  public void ingestEventBatch_notAuthorized() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> createEventDto())
      .collect(toList());

    // when
    final Response ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, "wroooong")
      .execute();

    // then
    assertThat(ingestResponse.getStatus(), is(401));

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestEventBatch_limitExceeded() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getIngestionConfiguration()
      .setMaxBatchRequestBytes(1L);

    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> createEventDto())
      .collect(toList());

    // when
    final ErrorResponseDto ingestResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute(ErrorResponseDto.class, SC_REQUEST_ENTITY_TOO_LARGE);

    // then
    assertThat(ingestResponse.getErrorMessage(), containsString("Request too large"));

    assertEventDtosArePersisted(Collections.emptyList());
  }

  @Test
  public void ingestEventBatch_contentLengthHeaderMissing() throws IOException {
    // given

    // when
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
      assertThat(response.getStatusLine().getStatusCode(), is(SC_LENGTH_REQUIRED));
      final ErrorResponseDto errorResponseDto = embeddedOptimizeExtension.getObjectMapper()
        .readValue(response.getEntity().getContent(), ErrorResponseDto.class);

      assertThat(errorResponseDto.getErrorMessage(), is(MESSAGE_NO_CONTENT_LENGTH));

      assertEventDtosArePersisted(Collections.emptyList());
    }
  }

  @Test
  public void ingestEventBatch_rejectInvalidPropertyValues() {
    // given
    final List<EventDto> eventDtos = IntStream.range(0, 2)
      .mapToObj(operand -> createEventDto())
      .collect(toList());

    final EventDto invalidEventDto1 = createEventDto();
    invalidEventDto1.setId(null);
    eventDtos.add(invalidEventDto1);

    final EventDto invalidEventDto2 = createEventDto();
    invalidEventDto2.setDuration(-500L);
    eventDtos.add(invalidEventDto2);

    // when
    final ValidationErrorResponseDto ingestErrorResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute(ValidationErrorResponseDto.class, SC_BAD_REQUEST);

    // then
    assertThat(ingestErrorResponse.getErrorMessage(), is(THE_REQUEST_BODY_WAS_INVALID));
    assertThat(ingestErrorResponse.getValidationErrors().size(), is(2));
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getProperty)
        .collect(toList()),
      containsInAnyOrder("element[2]." + id, "element[3]." + duration)
    );
    assertThat(
      ingestErrorResponse.getValidationErrors()
        .stream()
        .map(ValidationErrorResponseDto.ValidationError::getErrorMessage)
        .collect(toList()),
      everyItem(is(notNullValue()))
    );

    assertEventDtosArePersisted(Collections.emptyList());
  }

  private void assertEventDtosArePersisted(final List<EventDto> eventDtos) {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final SearchResponse eventSearchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(EVENT_INDEX_NAME);
    assertThat(eventSearchResponse.getHits().getTotalHits(), is((long) eventDtos.size()));
    final List<EventDto> indexedEventDtos = Arrays.stream(eventSearchResponse.getHits().getHits())
      .map(this::readAsEventDto)
      .collect(toList());
    assertThat(indexedEventDtos, containsInAnyOrder(eventDtos.toArray()));
  }

  @SneakyThrows
  private EventDto readAsEventDto(final SearchHit hit) {
    return embeddedOptimizeExtension.getObjectMapper().readValue(hit.getSourceAsString(), EventDto.class);
  }

  private String getApiSecret() {
    return embeddedOptimizeExtension.getConfigurationService().getIngestionConfiguration().getApiSecret();
  }

  private EventDto createEventDto() {
    return new EventDto(
      UUID.randomUUID().toString(),
      RandomStringUtils.randomAlphabetic(10),
      System.currentTimeMillis(),
      System.currentTimeMillis(),
      RandomStringUtils.randomAlphabetic(10),
      Math.abs(RANDOM.nextLong()),
      RandomStringUtils.randomAlphabetic(10),
      RandomStringUtils.randomAlphabetic(10),
      ImmutableMap.of(
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
        RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
      )
    );
  }

}
