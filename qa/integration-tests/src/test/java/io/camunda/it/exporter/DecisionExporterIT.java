/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static io.camunda.qa.util.cluster.TestStandaloneCamunda.ELASTIC_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class DecisionExporterIT {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      new ElasticsearchContainer(ELASTIC_IMAGE)
          // use JVM option files to avoid overwriting default options set by the ES container class
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
              BindMode.READ_ONLY)
          // can be slow in CI
          .withStartupTimeout(Duration.ofMinutes(5))
          .withEnv("action.auto_create_index", "true")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false");

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withCamundaExporter("http://" + ELASTICSEARCH_CONTAINER.getHttpHostAddress())
          .withProperty("zeebe.broker.gateway.enable", true)
          .withProperty("camunda.rest.query.enabled", true);

  private ZeebeClient client;

  @BeforeEach
  void setUp() {
    client = broker.newClientBuilder().build();
  }

  @Test
  void shouldExportDecision() {
    // given
    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .send()
            .join();
    final var expectedDecisionName = resource.getDecisions().get(0).getDmnDecisionName();

    // when
    // broker has exported
    // TODO: Add a generic way to wait until exporter has exported all records.

    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .until(() -> !client.newDecisionDefinitionQuery().send().join().items().isEmpty());

    // then
    final var result = client.newDecisionDefinitionQuery().send().join();
    assertThat(result.items().get(0).getDmnDecisionName()).isEqualTo(expectedDecisionName);
  }
}
