/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@AllArgsConstructor
@Component
@Slf4j
public class TelemetrySendingService {

  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public TelemetrySendingService() {
    httpClient = HttpClients.createDefault();
    objectMapper = new ObjectMapper();
  }

  public void sendTelemetryData(final TelemetryDataDto telemetryData,
                                final String telemetryEndpoint) {
    log.info("Sending telemetry to {}.", telemetryEndpoint);

    try {
      final HttpPost request = new HttpPost(telemetryEndpoint);
      final StringEntity telemetryAsString = new StringEntity(
        objectMapper.writeValueAsString(telemetryData),
        ContentType.APPLICATION_JSON
      );
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      request.setEntity(telemetryAsString);

      final CloseableHttpResponse response = httpClient.execute(request);
      final Response.Status statusCode = Response.Status.fromStatusCode(
        response.getStatusLine().getStatusCode()
      );
      if (!Response.Status.ACCEPTED.equals(statusCode)) {
        throw new OptimizeRuntimeException("Unexpected response when sending telemetry data: " + statusCode);
      }
    } catch (IllegalArgumentException e) {
      throw new OptimizeValidationException("Telemetry endpoint not configured correctly");
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem sending telemetry data.", e);
    }
  }
}
