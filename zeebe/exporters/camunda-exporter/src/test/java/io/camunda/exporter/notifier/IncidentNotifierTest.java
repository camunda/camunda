/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.notifier;

import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_ALERTS;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_BPMN_PROCESS_ID;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_CREATION_TIME;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_ERROR_MESSAGE;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_ERROR_TYPE;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_FLOW_NODE_ID;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_JOB_KEY;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_MESSAGE;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_PROCESS_INSTANCE_ID;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_PROCESS_KEY;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_PROCESS_NAME;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_PROCESS_VERSION;
import static io.camunda.exporter.notifier.IncidentNotifier.FIELD_NAME_STATE;
import static io.camunda.exporter.notifier.IncidentNotifier.MESSAGE;
import static io.camunda.webapps.schema.entities.incident.ErrorType.JOB_NO_RETRIES;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IncidentNotifierTest {
  protected static final String ALERT_WEBHOOKURL_URL = "http://WEBHOOKURL/path";
  private final String m2mToken = "mockM2mToken";
  private final String incident1Id = "incident1";
  private final String incident2Id = "incident2";
  private final Long processInstanceKey = 123L;
  private final String errorMessage = "errorMessage";
  private final ErrorType errorType = JOB_NO_RETRIES;
  private final String flowNodeId = "flowNodeId1";
  private final Long flowNodeInstanceId = 234L;
  private final Long processDefinitionKey = 345L;
  private final Long jobKey = 456L;
  private final IncidentState incidentState = IncidentState.ACTIVE;
  private final String bpmnProcessId = "testProcessId";
  private final String processName = "processName";
  private final String processVersion = "234";
  private final M2mTokenManager m2mTokenManager = mock(M2mTokenManager.class);
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);
  private final HttpClient httpClient = mock(HttpClient.class);

  private IncidentNotifier incidentNotifier;

  @BeforeEach
  public void setup() {
    final IncidentNotifierConfiguration configuration = new IncidentNotifierConfiguration();
    configuration.setWebhook(ALERT_WEBHOOKURL_URL);
    incidentNotifier =
        new IncidentNotifier(
            m2mTokenManager,
            processCache,
            configuration,
            httpClient,
            null,
            TestObjectMapper.objectMapper());
    when(processCache.get(any()))
        .thenReturn(Optional.of(new CachedProcessEntity(processName, processVersion, null)));
  }

  @Test
  public void shouldSendNotificationWithIncidents() throws IOException, InterruptedException {
    // given
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    final HttpResponse mock = mock(HttpResponse.class);
    when(mock.statusCode()).thenReturn(204);
    when(httpClient.send(any(), any())).thenReturn(mock);

    // when
    final List<IncidentEntity> incidents =
        asList(createIncident(incident1Id), createIncident(incident2Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(requestCaptor.capture(), eq(HttpResponse.BodyHandlers.ofString()));
    final HttpRequest request = requestCaptor.getValue();
    assertThat(request.uri().toString()).isEqualTo(ALERT_WEBHOOKURL_URL);
    assertThat(request.headers().allValues("Authorization").getFirst())
        .isEqualTo("Bearer " + m2mToken);

    final String body = extractBody(request.bodyPublisher().orElseThrow());

    // assert body
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class)).isEqualTo(2);
    assertThat(jsonContext.read(alerts + "[0].id", String.class)).isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
    assertThat(jsonContext.read(alerts + "[1].id", String.class)).isEqualTo(incident2Id);
    assertIncidentFields(jsonContext.read(alerts + "[1]", HashMap.class));
  }

  @Test
  public void shouldRequestNewTokenAndRetryWhenTokenIsInvalid()
      throws IOException, InterruptedException {
    // given
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    // the first call will return UNAUTHORIZED, the second - OK
    final HttpResponse mock = mock(HttpResponse.class);
    when(mock.statusCode()).thenReturn(401).thenReturn(200);
    given(httpClient.send(any(), any())).willReturn(mock, mock);

    // when
    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // then
    // new token was requested
    verify(m2mTokenManager).getToken(eq(true));
    // incident data was sent
    final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(2))
        .send(requestCaptor.capture(), eq(HttpResponse.BodyHandlers.ofString()));

    final HttpRequest request = requestCaptor.getValue();
    assertThat(request.headers().allValues("Authorization").getFirst())
        .isEqualTo("Bearer " + m2mToken);
    assertThat(request.uri().toString()).isEqualTo(ALERT_WEBHOOKURL_URL);

    final String body = extractBody(request.bodyPublisher().orElseThrow());
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class)).isEqualTo(1);
    assertThat(jsonContext.read(alerts + "[0].id", String.class)).isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
  }

  @Test
  public void shouldFailSilentlyOnErrorResponse() throws IOException, InterruptedException {
    // given
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    // webhook returns status 500
    final HttpResponse mock = mock(HttpResponse.class);
    when(mock.statusCode()).thenReturn(500);
    given(httpClient.send(any(), any())).willReturn(mock);

    final List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    // when
    final ThrowingCallable notify = () -> incidentNotifier.notifyOnIncidents(incidents);

    // then
    assertThatCode(notify).doesNotThrowAnyException();
  }

  @Test
  public void shouldFailSilentlyOnErrorAfterReAuthorization()
      throws IOException, InterruptedException {
    // given
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    // the first call will return UNAUTHORIZED, the second - 500
    final HttpResponse mock = mock(HttpResponse.class);
    when(mock.statusCode()).thenReturn(401).thenReturn(500);
    given(httpClient.send(any(), any())).willReturn(mock, mock);

    final List<IncidentEntity> incidents = List.of(createIncident(incident1Id));

    // when
    final ThrowingCallable notify = () -> incidentNotifier.notifyOnIncidents(incidents);

    // then
    assertThatCode(notify).doesNotThrowAnyException();
    // new token was requested
    verify(m2mTokenManager).getToken(eq(true));
  }

  @Test
  public void shouldFailSilentlyIfConnectionFailed() throws IOException, InterruptedException {
    // given
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    // notification will throw exception
    given(httpClient.send(any(), any())).willThrow(new RuntimeException("Something went wrong"));

    final List<IncidentEntity> incidents = List.of(createIncident(incident1Id));

    // when
    final ThrowingCallable notify = () -> incidentNotifier.notifyOnIncidents(incidents);

    // then
    assertThatCode(notify).doesNotThrowAnyException();
  }

  private String extractBody(final HttpRequest.BodyPublisher bodyPublisher)
      throws IOException, InterruptedException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final CountDownLatch latch = new CountDownLatch(1);

    bodyPublisher.subscribe(
        new Subscriber<>() {
          @Override
          public void onSubscribe(final Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final ByteBuffer item) {
            outputStream.write(item.array(), item.position(), item.remaining());
          }

          @Override
          public void onError(final Throwable throwable) {
            latch.countDown();
            throw new RuntimeException(throwable);
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });

    latch.await(); // Wait for the body to be fully written
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private void assertIncidentFields(final HashMap incidentFields) {
    assertThat(incidentFields.get(FIELD_NAME_MESSAGE)).isEqualTo(MESSAGE);
    assertThat(incidentFields.get(FIELD_NAME_JOB_KEY)).isEqualTo(jobKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_KEY))
        .isEqualTo(processDefinitionKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_BPMN_PROCESS_ID)).isEqualTo(bpmnProcessId);
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_NAME)).isEqualTo(processName);
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_VERSION)).isEqualTo(processVersion);
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_INSTANCE_KEY))
        .isEqualTo(flowNodeInstanceId.intValue());
    assertThat(incidentFields.get(FIELD_NAME_CREATION_TIME)).isNotNull();
    assertThat(incidentFields.get(FIELD_NAME_ERROR_MESSAGE)).isEqualTo(errorMessage);
    assertThat(incidentFields.get(FIELD_NAME_ERROR_TYPE)).isEqualTo(errorType.name());
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_ID)).isEqualTo(flowNodeId);
    assertThat(incidentFields.get(FIELD_NAME_STATE)).isEqualTo(incidentState.name());
    assertThat(incidentFields.get(FIELD_NAME_PROCESS_INSTANCE_ID))
        .isEqualTo(String.valueOf(processInstanceKey));
  }

  private IncidentEntity createIncident(final String id) {
    return new IncidentEntity()
        .setId(id)
        .setCreationTime(OffsetDateTime.now())
        .setProcessInstanceKey(processInstanceKey)
        .setErrorMessage(errorMessage)
        .setErrorType(errorType)
        .setFlowNodeId(flowNodeId)
        .setFlowNodeInstanceKey(flowNodeInstanceId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(bpmnProcessId)
        .setJobKey(jobKey)
        .setState(incidentState);
  }
}
