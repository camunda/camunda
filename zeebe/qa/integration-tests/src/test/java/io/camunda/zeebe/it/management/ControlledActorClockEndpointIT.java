/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ControlledActorClockEndpointIT {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withProperty("zeebe.clock.controlled", true);

  @AutoClose private final CamundaClient camundaClient = broker.newClientBuilder().build();

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void testPinningTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is pinned
    final var now = System.currentTimeMillis();
    final var request =
        buildRequest("pin")
            .POST(BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(Map.of("epochMilli", now))))
            .build();
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();
    final var response = httpClient.send(request, newResponseHandler());

    // when - producing records
    camundaClient.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
    RecordingExporter.records().limit(1).await();

    // then - records are exported with a timestamp matching the pinned time
    assertThat(response.statusCode()).as("/pin request was successful").isEqualTo(200);
    assertThat(response.body().epochMilli()).as("response returned pinned time").isEqualTo(now);
    assertThat(RecordingExporter.getRecords())
        .as("all exported records after /pin have the pinned timestamp")
        .isNotEmpty()
        .allSatisfy(record -> RecordAssert.assertThat(record).hasTimestamp(now));
  }

  @Test
  void testOffsetTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is offset
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();
    final var offset = Duration.ofHours(5);
    final var request =
        buildRequest("add")
            .POST(
                BodyPublishers.ofByteArray(
                    MAPPER.writeValueAsBytes(Map.of("offsetMilli", offset.toMillis()))))
            .build();
    final var beforeRecords = Instant.now();
    final var response = httpClient.send(request, newResponseHandler());

    // when - producing records
    camundaClient.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
    RecordingExporter.records().limit(1).await();

    // then - records are exported with a timestamp matching the offset time
    final var expectedUpperBound = Instant.now().plus(offset);
    final var expectedLowerBound = beforeRecords.plus(offset);
    assertThat(response.statusCode()).as("/add request was successful").isEqualTo(200);
    assertThat(response.body().instant())
        .as("returned instant is close to expected bound")
        .isCloseTo(expectedUpperBound, within(30, ChronoUnit.SECONDS));
    assertThat(RecordingExporter.getRecords())
        .as("all exported records after the /add request have an offset of 5 hours")
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(Instant.ofEpochMilli(record.getTimestamp()))
                    .as("record has offset timestamp")
                    .isBefore(expectedUpperBound)
                    .isAfter(expectedLowerBound));
  }

  private Builder buildRequest(final String operation) {
    return HttpRequest.newBuilder(broker.actuatorUri("clock", operation))
        .headers("Content-Type", "application/json", "Accept", "application/json");
  }

  private BodyHandler<Response> newResponseHandler() {
    return responseInfo ->
        BodySubscribers.mapping(BodySubscribers.ofByteArray(), this::mapResponse);
  }

  private Response mapResponse(final byte[] bytes) {
    try {
      return MAPPER.readValue(bytes, Response.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Response(
      @JsonProperty("epochMilli") long epochMilli, @JsonProperty("instant") Instant instant) {}
}
