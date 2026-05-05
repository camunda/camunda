/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full e2e: real Zeebe broker → analytics exporter → real OTel Collector. Deploys a process,
 * creates an instance, verifies the collector received the event with correct attributes.
 */
@Testcontainers
@ZeebeIntegration
final class AnalyticsExporterIT {

  private static final String PROCESS_ID = "analytics-e2e-test";
  private static final List<String> COLLECTOR_LOGS = new CopyOnWriteArrayList<>();

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> OTEL_COLLECTOR =
      new GenericContainer<>(
              DockerImageName.parse("otel/opentelemetry-collector-contrib").withTag("0.119.0"))
          .withCopyToContainer(
              Transferable.of(
                  """
                      receivers:
                        otlp:
                          protocols:
                            http:
                              endpoint: 0.0.0.0:4318
                      exporters:
                        debug:
                          verbosity: detailed
                      service:
                        pipelines:
                          logs:
                            receivers: [otlp]
                            exporters: [debug]
                      """),
              "/etc/otelcol-contrib/config.yaml")
          .withLogConsumer(frame -> COLLECTOR_LOGS.add(frame.getUtf8String()))
          .withExposedPorts(4318);

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withProperty("zeebe.broker.cluster.clusterId", "e2e-test-cluster");

  @AutoClose private CamundaClient client;

  @Test
  void shouldExportProcessInstanceCreatedToOtelCollector() {
    COLLECTOR_LOGS.clear();

    // given — broker with analytics exporter pointing at the collector
    broker
        .withExporter(
            "analytics",
            cfg -> {
              cfg.setClassName("io.camunda.exporter.analytics.AnalyticsExporter");
              cfg.setArgs(
                  Map.of(
                      "endpoint",
                      "http://localhost:" + OTEL_COLLECTOR.getMappedPort(4318),
                      "enabled",
                      true,
                      "pushInterval",
                      "PT1S",
                      "maxBatchSize",
                      10));
            })
        .start()
        .awaitCompleteTopology();

    client = broker.newClientBuilder().build();

    final var process = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
    client.newDeployResourceCommand().addProcessModel(process, PROCESS_ID + ".bpmn").send().join();

    // when — create a single process instance
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    // then — collector received exactly the event with correct attributes
    Awaitility.await("OTel Collector receives process_instance_created event")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              // event.name attribute with exact value
              assertThat(COLLECTOR_LOGS)
                  .anyMatch(line -> line.contains("event.name: Str(process_instance_created)"));

              // bpmn process id matches the deployed process
              assertThat(COLLECTOR_LOGS)
                  .anyMatch(
                      line -> line.contains("camunda.bpmn_process_id: Str(" + PROCESS_ID + ")"));

              // resource attributes
              assertThat(COLLECTOR_LOGS)
                  .anyMatch(line -> line.contains("service.name: Str(camunda-zeebe)"));
              assertThat(COLLECTOR_LOGS)
                  .anyMatch(line -> line.contains("camunda.cluster.id: Str(e2e-test-cluster)"));
              assertThat(COLLECTOR_LOGS)
                  .anyMatch(line -> line.contains("camunda.partition.id: Int(1)"));
            });
  }
}
