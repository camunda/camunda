/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.actuator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;

public class ClockActuatorClient {
  private final HttpClient httpClient = HttpClient.newBuilder().build();
  private final ObjectWriter objectWriter = new ObjectMapper().writer();
  private final String zeebeMonitoringAddress;

  public ClockActuatorClient(final String zeebeMonitoringAddress) {
    this.zeebeMonitoringAddress = zeebeMonitoringAddress;
  }

  public void resetZeebeTime() throws IOException, InterruptedException {
    zeebeRequest("DELETE", "/actuator/clock", null);
  }

  public Instant pinZeebeTime(final Instant pinAt) throws IOException, InterruptedException {
    zeebeRequest("POST", "/actuator/clock/pin", new PinRequestDto(pinAt));
    return pinAt;
  }

  public Duration offsetZeebeTime(final Duration offsetBy)
      throws IOException, InterruptedException {
    zeebeRequest("POST", "/actuator/clock/add", new OffsetRequestDto(offsetBy));
    return offsetBy;
  }

  private void zeebeRequest(final String method, final String endpoint, final RequestDto requestDto)
      throws IOException, InterruptedException {
    final var uri = URI.create(String.format("http://%s/%s", zeebeMonitoringAddress, endpoint));
    final BodyPublisher body;
    if (requestDto == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(objectWriter.writeValueAsBytes(requestDto));
    }
    final var httpRequest =
        HttpRequest.newBuilder(uri)
            .method(method, body)
            .header("Content-Type", "application/json")
            .build();
    final var httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Pinning time failed: " + httpResponse.body());
    }
  }

  private static final class PinRequestDto implements RequestDto {
    @JsonProperty("epochMilli")
    private final long epochMilli;

    private PinRequestDto(final Instant pinAt) {
      epochMilli = pinAt.toEpochMilli();
    }
  }

  private static final class OffsetRequestDto implements RequestDto {
    @JsonProperty("offsetMilli")
    private final long offsetMilli;

    private OffsetRequestDto(final Duration offsetBy) {
      offsetMilli = offsetBy.toMillis();
    }
  }

  private interface RequestDto {}
}
