/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.easytelemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Component
@Conditional(CamundaPlatformCondition.class)
@Slf4j
public class EasyTelemetrySendingService {

  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public EasyTelemetrySendingService(final ObjectMapper objectMapper) {
    this(HttpClients.createDefault(), objectMapper);
  }

  public EasyTelemetrySendingService(final CloseableHttpClient httpClient, final ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
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

      try (final CloseableHttpResponse response = httpClient.execute(request)) {
        final Response.Status statusCode = Response.Status.fromStatusCode(
          response.getStatusLine().getStatusCode()
        );
        if (!Response.Status.ACCEPTED.equals(statusCode)) {
          throw new OptimizeRuntimeException("Unexpected response when sending telemetry data: " + statusCode);
        }
      }
    } catch (IllegalArgumentException e) {
      throw new OptimizeValidationException("Telemetry endpoint not configured correctly");
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem sending telemetry data.", e);
    }
  }
}
