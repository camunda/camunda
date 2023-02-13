/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelClient;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEvent;
import org.camunda.optimize.service.telemetry.mixpanel.client.EventReportingEvent;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEventProperties;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MixpanelClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  @Mock
  private CloseableHttpClient httpClient;

  private MixpanelClient mixpanelClient;

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create().captureForType(MixpanelClient.class);

  @BeforeEach
  public void setup() {
    this.mixpanelClient = new MixpanelClient(configurationService, objectMapper, httpClient);
  }

  @SneakyThrows
  @Test
  public void mixpanelEventImportWithExpectedParametersAndPayload() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(Response.Status.OK);
    when(mockResponse.getEntity()).thenReturn(new StringEntity("{}", ContentType.APPLICATION_JSON));
    when(httpClient.execute(any())).thenReturn(mockResponse);

    final String stage = "unit-test";
    final String organizationId = "orgId";
    final String clusterId = "clusterId";
    final MixpanelEventProperties mixpanelEventProperties = new MixpanelEventProperties(
      stage, organizationId, clusterId
    );

    // when
    mixpanelClient.importEvent(new MixpanelEvent(EventReportingEvent.HEARTBEAT, mixpanelEventProperties));
    ArgumentCaptor<HttpPost> requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
    verify(httpClient, times(1)).execute(requestCaptor.capture());

    // then
    assertThat(requestCaptor.getValue().getURI().toString())
      .startsWith(getMixpanelConfiguration().getImportUrl());
    assertThat(requestCaptor.getValue().getURI().getQuery())
      .isEqualTo("strict=1&project_id=" + getMixpanelConfiguration().getProjectId());
    assertThat(requestCaptor.getValue().getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(requestCaptor.getValue().getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())
      .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(requestCaptor.getValue().getFirstHeader(HttpHeaders.AUTHORIZATION).getValue())
      .satisfies(authHeaderValue -> {
        assertThat(authHeaderValue).contains("Basic ");
        final String plainCredentials = new String(
          Base64.getDecoder().decode(authHeaderValue.split("\\s")[1]), StandardCharsets.UTF_8
        );
        assertThat(plainCredentials.split(":")).containsExactly(username, secret);
      });
    final MixpanelEvent recordedMixpanelEvent = readMixpanelEventFromRequest(requestCaptor);
    assertThat(recordedMixpanelEvent.getEvent()).isEqualTo(MixpanelEvent.EVENT_NAME_PREFIX + EventReportingEvent.HEARTBEAT);
    assertThat(recordedMixpanelEvent.getProperties()).isEqualTo(mixpanelEventProperties);
  }

  @SneakyThrows
  @Test
  public void mixpanelEventImportFailsOnNonOkStatusCode() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(Response.Status.BAD_REQUEST);
    when(mockResponse.getEntity()).thenReturn(new StringEntity("{}", ContentType.APPLICATION_JSON));
    when(httpClient.execute(any())).thenReturn(mockResponse);

    // when
    final MixpanelEvent event = new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties());
    assertThatThrownBy(() -> mixpanelClient.importEvent(event))
      // then
      .isInstanceOf(OptimizeRuntimeException.class)
      .hasMessage("Unexpected response status on a mixpanel import: 400, response body: {}");
  }

  @SneakyThrows
  @Test
  public void mixpanelEventImportFailsOnErrorInResponseBody() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(Response.Status.OK);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
      "{\"error\":\"failure\"}", ContentType.APPLICATION_JSON
    ));
    when(httpClient.execute(any())).thenReturn(mockResponse);

    // when
    final MixpanelEvent event = new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties());
    assertThatThrownBy(() -> mixpanelClient.importEvent(event))
      // then
      .isInstanceOf(OptimizeRuntimeException.class)
      .hasMessage("Mixpanel import was not successful, error: failure");
  }

  @SneakyThrows
  @Test
  public void mixpanelEventImportInvalidJsonResponseIsHandledGracefullyButLogged() {
    // given
    final String username = "username";
    final String secret = "secret";
    getMixpanelConfiguration().getServiceAccount().setUsername(username);
    getMixpanelConfiguration().getServiceAccount().setSecret(secret);

    final CloseableHttpResponse mockResponse = mockResponseWithStatus(Response.Status.OK);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
      "{\"error\":", ContentType.APPLICATION_JSON
    ));
    when(httpClient.execute(any())).thenReturn(mockResponse);

    // when
    mixpanelClient.importEvent(new MixpanelEvent(EventReportingEvent.HEARTBEAT, new MixpanelEventProperties()));

    // then
    logCapturer.assertContains("Could not parse response from Mixpanel.");
  }

  @NonNull
  private CloseableHttpResponse mockResponseWithStatus(final Response.Status status) {
    final CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
    final StatusLine mockStatusLine = mock(StatusLine.class);
    when(mockStatusLine.getStatusCode()).thenReturn(status.getStatusCode());
    when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    return mockResponse;
  }

  private MixpanelConfiguration getMixpanelConfiguration() {
    return configurationService.getAnalytics().getMixpanel();
  }

  @SneakyThrows
  private MixpanelEvent readMixpanelEventFromRequest(final ArgumentCaptor<HttpPost> requestCaptor) {
    try (final Reader reader = new InputStreamReader(requestCaptor.getValue().getEntity().getContent())) {
      return objectMapper.readValue(CharStreams.toString(reader), MixpanelEvent[].class)[0];
    }
  }

}
