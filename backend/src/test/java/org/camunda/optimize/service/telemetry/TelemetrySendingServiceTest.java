/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import lombok.SneakyThrows;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelemetrySendingServiceTest {

  private static final String TEST_ENDPOINT = "testEndpoint";

  @SneakyThrows
  @Test
  public void telemetryDataIsSentWithCorrectPayload() {
    // given
    final CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
    final TelemetrySendingService underTest = new TelemetrySendingService(mockClient, new ObjectMapper());

    // mock successful response
    final CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
    final StatusLine mockStatusLine = mock(StatusLine.class);
    when(mockStatusLine.getStatusCode()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
    when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    when(mockClient.execute(any())).thenReturn(mockResponse);

    // when
    sendTelemetry(underTest);
    ArgumentCaptor<HttpPost> requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
    verify(mockClient, times(1)).execute(requestCaptor.capture());

    // then a post request to the correct endpoint and with the correct entity json is sent
    assertThat(requestCaptor.getValue().getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(requestCaptor.getValue().getURI()).hasToString(TEST_ENDPOINT);
    assertThat(getEntityJsonFromRequest(requestCaptor.getValue())).isEqualTo(getExpectedRequestEntityJson());
  }

  @SneakyThrows
  @Test
  public void telemetryDataIsSent_withUnexpectedResponse_throwsRuntimeException() {
    // given
    final CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
    final TelemetrySendingService underTest = new TelemetrySendingService(mockClient, new ObjectMapper());

    // mock unexpected response
    final CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
    final StatusLine mockStatusLine = mock(StatusLine.class);
    when(mockStatusLine.getStatusCode()).thenReturn(Response.Status.BAD_REQUEST.getStatusCode());
    when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    when(mockClient.execute(any())).thenReturn(mockResponse);

    // when/then
    assertThrows(OptimizeRuntimeException.class, () -> sendTelemetry(underTest));
  }

  @SneakyThrows
  @Test
  public void telemetryDataIsSent_withIOException_throwsRuntimeException() {
    // given
    final CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
    final TelemetrySendingService underTest = new TelemetrySendingService(mockClient, new ObjectMapper());

    // mock IOException when executing request
    when(mockClient.execute(any())).thenThrow(IOException.class);

    // when/then
    assertThrows(OptimizeRuntimeException.class, () -> sendTelemetry(underTest));
  }

  private void sendTelemetry(final TelemetrySendingService underTest) {
    underTest.sendTelemetryData(
      getTestTelemetryData(),
      TEST_ENDPOINT
    );
  }

  private TelemetryDataDto getTestTelemetryData() {
    final DatabaseDto databaseDto = DatabaseDto.builder().version("7.0.0").build();
    final InternalsDto internalsDto = InternalsDto.builder().database(databaseDto).build();
    final ProductDto productDto = ProductDto.builder().internals(internalsDto).build();

    return TelemetryDataDto.builder()
      .installation("1234-5678")
      .product(productDto)
      .build();
  }

  @SneakyThrows
  private String getExpectedRequestEntityJson() {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.writeValueAsString(getTestTelemetryData());
  }

  @SneakyThrows
  private String getEntityJsonFromRequest(final HttpPost request) {
    final Reader reader = new InputStreamReader(request.getEntity().getContent());
    return CharStreams.toString(reader);
  }
}
