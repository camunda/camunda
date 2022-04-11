/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;

import static javax.ws.rs.HttpMethod.POST;

public class ClockActuatorClient {
  private final HttpClient httpClient = HttpClient.newBuilder().build();
  private final ObjectWriter objectWriter = new ObjectMapper().writer();
  private final String monitoringAddress;

  public ClockActuatorClient(final String zeebeMonitoringAddress) {
    monitoringAddress = zeebeMonitoringAddress;
  }

  public Instant pinZeebeTime(final Instant pinAt) throws IOException, InterruptedException {
    sendRequest(POST, "actuator/clock/pin", new PinRequestDto(pinAt));
    return pinAt;
  }

  private void sendRequest(final String method, final String endpoint, final PinRequestDto requestDto)
    throws IOException, InterruptedException {
    final URI uri = URI.create(String.format("http://%s/%s", monitoringAddress, endpoint));
    final BodyPublisher body;
    if (requestDto == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(objectWriter.writeValueAsBytes(requestDto));
    }
    final HttpRequest httpRequest =
      HttpRequest.newBuilder(uri)
        .method(method, body)
        .header("Content-Type", "application/json")
        .build();
    final HttpResponse<String> httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Pinning time failed: " + httpResponse.body());
    }
  }

  private static final class PinRequestDto {
    @JsonProperty("epochMilli")
    private final long epochMilli;

    private PinRequestDto(final Instant pinAt) {
      epochMilli = pinAt.toEpochMilli();
    }
  }

}