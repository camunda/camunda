/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import io.camunda.optimize.service.mixpanel.client.MixpanelClient;
import io.camunda.optimize.service.mixpanel.client.MixpanelEvent;
import io.camunda.optimize.service.mixpanel.client.MixpanelEventProperties;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import jakarta.ws.rs.HttpMethod;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
public class MixpanelClientTest {

  @RegisterExtension
  protected final LogCapturer logCapturer =
      LogCapturer.create().captureForType(MixpanelClient.class);

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final ConfigurationService configurationService =
      ConfigurationServiceBuilder.createDefaultConfiguration();
  @Mock private CloseableHttpClient httpClient;
  private MixpanelClient mixpanelClient;

  @BeforeEach
  public void setup() {
    mixpanelClient = new MixpanelClient(configurationService, objectMapper, httpClient);
  }

  @Test
  public void mixpanelEventImportWithExpectedParametersAndPayload() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(HttpStatus.OK);
    when(mockResponse.getEntity()).thenReturn(new StringEntity("{}", ContentType.APPLICATION_JSON));
    try {
      when(httpClient.execute(any())).thenReturn(mockResponse);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    final String stage = "unit-test";
    final String organizationId = "orgId";
    final String clusterId = "clusterId";
    final MixpanelEventProperties mixpanelEventProperties =
        new MixpanelEventProperties(stage, organizationId, clusterId);

    // when
    mixpanelClient.importEvent(
        new MixpanelEvent(EventReportingEvent.HEARTBEAT, mixpanelEventProperties));
    final ArgumentCaptor<HttpPost> requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
    try {
      verify(httpClient, times(1)).execute(requestCaptor.capture());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    // then
    assertThat(requestCaptor.getValue().getURI().toString())
        .startsWith(getMixpanelConfiguration().getImportUrl());
    assertThat(requestCaptor.getValue().getURI().getQuery())
        .isEqualTo("strict=1&project_id=" + getMixpanelConfiguration().getProjectId());
    assertThat(requestCaptor.getValue().getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(requestCaptor.getValue().getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())
        .isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(requestCaptor.getValue().getFirstHeader(HttpHeaders.AUTHORIZATION).getValue())
        .satisfies(
            authHeaderValue -> {
              assertThat(authHeaderValue).contains("Basic ");
              final String plainCredentials =
                  new String(
                      Base64.getDecoder().decode(authHeaderValue.split("\\s")[1]),
                      StandardCharsets.UTF_8);
              assertThat(plainCredentials.split(":")).containsExactly(username, secret);
            });
    final MixpanelEvent recordedMixpanelEvent = readMixpanelEventFromRequest(requestCaptor);
    assertThat(recordedMixpanelEvent.getEvent())
        .isEqualTo(MixpanelEvent.EVENT_NAME_PREFIX + EventReportingEvent.HEARTBEAT);
    assertThat(recordedMixpanelEvent.getProperties()).isEqualTo(mixpanelEventProperties);
  }

  @Test
  public void mixpanelEventImportFailsOnNonOkStatusCode() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(HttpStatus.BAD_REQUEST);
    when(mockResponse.getEntity()).thenReturn(new StringEntity("{}", ContentType.APPLICATION_JSON));
    try {
      when(httpClient.execute(any())).thenReturn(mockResponse);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    // when
    final MixpanelEvent event =
        new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties());
    assertThatThrownBy(() -> mixpanelClient.importEvent(event))
        // then
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessage("Unexpected response status on a mixpanel import: 400, response body: {}");
  }

  @Test
  public void mixpanelEventImportFailsOnErrorInResponseBody() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(HttpStatus.OK);
    when(mockResponse.getEntity())
        .thenReturn(new StringEntity("{\"error\":\"failure\"}", ContentType.APPLICATION_JSON));
    try {
      when(httpClient.execute(any())).thenReturn(mockResponse);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    // when
    final MixpanelEvent event =
        new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties());
    assertThatThrownBy(() -> mixpanelClient.importEvent(event))
        // then
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessage("Mixpanel import was not successful, error: failure");
  }

  @Test
  public void mixpanelEventImportInvalidJsonResponseIsHandledGracefullyButLogged() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(HttpStatus.OK);
    when(mockResponse.getEntity())
        .thenReturn(new StringEntity("{\"error\":", ContentType.APPLICATION_JSON));
    try {
      when(httpClient.execute(any())).thenReturn(mockResponse);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    // when
    mixpanelClient.importEvent(
        new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties()));

    // then
    logCapturer.assertContains("Could not parse response from Mixpanel.");
  }

  private CloseableHttpResponse mockResponseWithStatus(final HttpStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("status cannot be null");
    }

    final CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
    final StatusLine mockStatusLine = mock(StatusLine.class);
    when(mockStatusLine.getStatusCode()).thenReturn(status.value());
    when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    return mockResponse;
  }

  private MixpanelConfiguration getMixpanelConfiguration() {
    return configurationService.getAnalytics().getMixpanel();
  }

  private MixpanelEvent readMixpanelEventFromRequest(final ArgumentCaptor<HttpPost> requestCaptor) {
    try {
      try (final Reader reader =
          new InputStreamReader(requestCaptor.getValue().getEntity().getContent())) {
        return objectMapper.readValue(CharStreams.toString(reader), MixpanelEvent[].class)[0];
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }
}
