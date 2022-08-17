/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.zeebe.util.Either;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

public final class PartitionsActuatorClient {
  private static final TypeReference<Map<String, PartitionStatus>> RESPONSE_TYPE =
      new TypeReference<>() {};
  private static final ObjectReader READER = new ObjectMapper().readerFor(RESPONSE_TYPE);
  private final HttpClient httpClient;
  private final String adminEndpoint;

  public PartitionsActuatorClient(final String adminEndpoint) {
    this(adminEndpoint, HttpClient.newBuilder().build());
  }

  public PartitionsActuatorClient(final String adminEndpoint, final HttpClient httpClient) {
    this.httpClient = httpClient;
    this.adminEndpoint = adminEndpoint;
  }

  public Either<Throwable, Map<String, PartitionStatus>> pauseExporting() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(getEndpoint("pauseExporting"))
            .timeout(Duration.ofSeconds(5))
            .build();

    return sendPartitionsRequest(request);
  }

  public Either<Throwable, Map<String, PartitionStatus>> resumeExporting() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(getEndpoint("resumeExporting"))
            .timeout(Duration.ofSeconds(5))
            .build();

    return sendPartitionsRequest(request);
  }

  public Either<Throwable, Map<String, PartitionStatus>> takeSnapshot() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(getEndpoint("takeSnapshot"))
            .timeout(Duration.ofSeconds(5))
            .build();

    return sendPartitionsRequest(request);
  }

  public Either<Throwable, Map<String, PartitionStatus>> queryPartitions() {
    final HttpRequest request =
        HttpRequest.newBuilder().GET().uri(getEndpoint("")).timeout(Duration.ofSeconds(5)).build();

    return sendPartitionsRequest(request);
  }

  private Either<Throwable, Map<String, PartitionStatus>> sendPartitionsRequest(
      final HttpRequest request) {
    final BodyHandler<byte[]> bodyHandler = BodyHandlers.ofByteArray();

    try {
      return Either.right(READER.readValue(httpClient.send(request, bodyHandler).body()));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return Either.left(e);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  private URI getEndpoint(final String path) {
    return URI.create(String.format("http://%s/actuator/partitions/%s", adminEndpoint, path));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PartitionStatus(
      String role,
      String snapshotId,
      Long processedPosition,
      Long processedPositionInSnapshot,
      String streamProcessorPhase,
      Long exportedPosition,
      String exporterPhase) {}
}
