/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.jackson.record.AbstractRecord;
import io.camunda.zeebe.test.util.actuator.ClockActuatorClient;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

final class ControlledActorClockEndpointIT {
  private static final String ELASTICSEARCH_HOST = "elasticsearch";
  private static final String INDEX_PREFIX = "exporter-clock-test";
  private Network network;
  private ElasticsearchContainer elasticsearchContainer;
  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  private ClockActuatorClient clockActuatorClient;

  @BeforeEach
  void startContainers() {
    network = Network.newNetwork();
    startElasticsearch();
    startZeebe();
  }

  @AfterEach
  void stopContainers() {
    CloseHelper.closeAll(zeebeClient, zeebeContainer, elasticsearchContainer, network);
  }

  @Test
  void testPinningTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is pinned
    final var pinnedAt = clockActuatorClient.pinZeebeTime(Instant.now().minus(Duration.ofDays(3)));
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when - producing records
    zeebeClient.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then - records are exported with a timestamp matching the pinned time
    waitForExportedRecords();
    final var records = searchExportedRecords();
    assertThat(records)
        .isNotEmpty() // double check that we have exported some records
        .allSatisfy(record -> assertThat(record.getTimestamp()).isEqualTo(pinnedAt.toEpochMilli()));
  }

  @Test
  void testOffsetTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is offset
    final var beforeRecords = Instant.now();
    final var offsetZeebeTime = clockActuatorClient.offsetZeebeTime(Duration.ofHours(5));
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when - producing records
    zeebeClient.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then - records are exported with a timestamp matching the offset time
    waitForExportedRecords();
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var records = searchExportedRecords();
              assertThat(records)
                  .isNotEmpty() // double check that we have exported some records
                  .allSatisfy(
                      (record) -> {
                        final var timestamp = Instant.ofEpochMilli(record.getTimestamp());
                        final var afterRecords = Instant.now();
                        assertThat(timestamp)
                            .isBefore(afterRecords.plus(offsetZeebeTime))
                            .isAfter(beforeRecords.plus(offsetZeebeTime));
                      });
            });
  }

  private List<AbstractRecord<?>> searchExportedRecords() throws IOException, InterruptedException {
    final var uri =
        URI.create(
            String.format(
                "http://%s/%s*/_search",
                elasticsearchContainer.getHttpHostAddress(), INDEX_PREFIX));
    final var request = HttpRequest.newBuilder(uri).method("POST", BodyPublishers.noBody()).build();
    final var response = httpClient.send(request, BodyHandlers.ofInputStream());
    final var result = mapper.readValue(response.body(), EsSearchResponseDto.class);
    if (result.esDocumentsWrapper == null) {
      return Collections.emptyList();
    }
    return result.esDocumentsWrapper.documents.stream()
        .map(esDocumentDto -> esDocumentDto.record)
        .collect(Collectors.toList());
  }

  void startElasticsearch() {
    final var version = RestClient.class.getPackage().getImplementationVersion();
    elasticsearchContainer =
        new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(version))
            .withNetwork(network)
            .withNetworkAliases(ELASTICSEARCH_HOST)
            .withCreateContainerCmdModifier(
                cmd ->
                    Objects.requireNonNull(cmd.getHostConfig())
                        .withMemory((long) (1024 * 1024 * 1024)));
    elasticsearchContainer.start();
  }

  private void startZeebe() {
    zeebeContainer =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withNetwork(network)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL",
                String.format("http://%s:9200", ELASTICSEARCH_HOST))
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", INDEX_PREFIX)
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true");
    zeebeContainer.start();
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .build();
    clockActuatorClient = new ClockActuatorClient(zeebeContainer.getExternalMonitoringAddress());
  }

  /**
   * Wait until all records are exported. We assume that all records are exported if the number of
   * records is does not change for 5 seconds. Initialize the counter to one so that we expect at
   * least one record and don't succeed if nothing is exported.
   */
  private void waitForExportedRecords() {
    final AtomicInteger previouslySeenRecords = new AtomicInteger(1);
    Awaitility.await("Waiting for a stable number of exported records")
        .during(Duration.ofSeconds(5))
        .until(
            this::searchExportedRecords,
            (records) -> {
              final var now = records.size();
              final var previous = previouslySeenRecords.getAndSet(now);
              return now == previous;
            });
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class EsDocumentDto {
    @JsonProperty(value = "_source", required = true)
    AbstractRecord<?> record;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class EsSearchResponseDto {
    @JsonProperty(value = "hits", required = true)
    EsDocumentsWrapper esDocumentsWrapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class EsDocumentsWrapper {
      @JsonProperty(value = "hits", required = true)
      final List<EsDocumentDto> documents = Collections.emptyList();
    }
  }
}
