/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.zeebe.util.Either;
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
  private final URI queryPartitionsUri;
  private final URI takeSnapshotUri;

  public PartitionsActuatorClient(final String adminEndpoint) {
    this(adminEndpoint, HttpClient.newBuilder().build());
  }

  public PartitionsActuatorClient(final String adminEndpoint, final HttpClient httpClient) {
    this.httpClient = httpClient;

    queryPartitionsUri = URI.create(String.format("http://%s/actuator/partitions", adminEndpoint));
    takeSnapshotUri =
        URI.create(String.format("http://%s/actuator/partitions/takeSnapshot", adminEndpoint));
  }

  public Either<Throwable, Map<String, PartitionStatus>> takeSnapshot() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(takeSnapshotUri)
            .timeout(Duration.ofSeconds(5))
            .build();

    return sendPartitionsRequest(request);
  }

  public Either<Throwable, Map<String, PartitionStatus>> queryPartitions() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .GET()
            .uri(queryPartitionsUri)
            .timeout(Duration.ofSeconds(5))
            .build();

    return sendPartitionsRequest(request);
  }

  private Either<Throwable, Map<String, PartitionStatus>> sendPartitionsRequest(
      final HttpRequest request) {
    final BodyHandler<byte[]> bodyHandler = BodyHandlers.ofByteArray();

    try {
      return Either.right(READER.readValue(httpClient.send(request, bodyHandler).body()));
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  public static final class PartitionStatus {
    public String role;
    public String snapshotId;
    public Long processedPosition;
    public Long processedPositionInSnapshot;
    public String streamProcessorPhase;
  }
}
