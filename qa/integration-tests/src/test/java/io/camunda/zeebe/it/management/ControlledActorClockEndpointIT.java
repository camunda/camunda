/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.engine.ContainerEngine;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// NOTE: once https://github.com/camunda-community-hub/zeebe-test-container/issues/318 is completed,
// we can reduce execution time by sharing the same engine and resetting the clock in between
@Testcontainers
final class ControlledActorClockEndpointIT {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private final ZeebeContainer container =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withEnv("ZEEBE_CLOCK_CONTROLLED", "true");

  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder()
          .withContainer(container)
          .withIdlePeriod(Duration.ofSeconds(2))
          .build();

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private ZeebeClient zeebeClient;

  @BeforeEach
  void beforeEach() {
    zeebeClient = engine.createClient();
  }

  @Test
  void testPinningTime() throws IOException, InterruptedException, TimeoutException {
    // given - Zeebe actor clock is pinned
    final var now = System.currentTimeMillis();
    final var request =
        buildRequest("pin")
            .POST(BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(Map.of("epochMilli", now))))
            .build();
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();
    final var response = httpClient.send(request, new ResponseHandler());

    // when - producing records
    zeebeClient.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // then - records are exported with a timestamp matching the pinned time
    engine.waitForIdleState(Duration.ofSeconds(5));
    assertThat(response.statusCode()).as("/pin request was successful").isEqualTo(200);
    assertThat(response.body().epochMilli()).as("response returned pinned time").isEqualTo(now);
    assertThat(getExportedRecords())
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
    final var response = httpClient.send(request, new ResponseHandler());

    // when - producing records
    zeebeClient.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // then - records are exported with a timestamp matching the offset time
    final var expectedUpperBound = Instant.now().plus(offset);
    final var expectedLowerBound = beforeRecords.plus(offset);
    assertThat(response.statusCode()).as("/add request was successful").isEqualTo(200);
    assertThat(response.body().instant())
        .as("returned instant is close to expected bound")
        .isCloseTo(expectedUpperBound, within(30, ChronoUnit.SECONDS));
    assertThat(getExportedRecords())
        .as("all exported records after the /add request have an offset of 5 hours")
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(Instant.ofEpochMilli(record.getTimestamp()))
                    .as("record has offset timestamp")
                    .isBefore(expectedUpperBound)
                    .isAfter(expectedLowerBound));
  }

  // it's necessary to make a copied stream out of this, as passing the iterable directly to AssertJ
  // will cause it to close the underlying stream after the assertion
  private Stream<Record<?>> getExportedRecords() {
    final var exportedRecords = BpmnAssert.getRecordStream().records();
    return StreamSupport.stream(exportedRecords.spliterator(), false);
  }

  private Builder buildRequest(final String operation) {
    return HttpRequest.newBuilder(
            URI.create(
                "http://"
                    + container.getExternalMonitoringAddress()
                    + "/actuator/clock/"
                    + operation))
        .headers("Content-Type", "application/json", "Accept", "application/json");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Response(
      @JsonProperty("epochMilli") long epochMilli, @JsonProperty("instant") Instant instant) {}

  private static final class ResponseHandler implements BodyHandler<Response> {

    @Override
    public BodySubscriber<Response> apply(final ResponseInfo responseInfo) {
      return HttpResponse.BodySubscribers.mapping(
          HttpResponse.BodySubscribers.ofInputStream(), this::unsafeRead);
    }

    private Response unsafeRead(final InputStream input) {
      try {
        return MAPPER.readValue(input, Response.class);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
