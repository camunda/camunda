/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class StartStandaloneCamundaWithUnifiedConfigurationCamundaDockerIT
    extends AbstractCamundaDockerIT {
  @Test
  public void testStartStandaloneCamundaWithUnifiedConfiguration() {
    // given
    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();

    final GenericContainer<?> camundaContainer =
        createContainer(this::createUnauthenticatedUnifiedConfigCamundaContainer);

    // when
    startContainer(camundaContainer);

    // then
    final String host = "http://" + camundaContainer.getHost() + ":";
    final URI camundaEndpoint = URI.create(host + camundaContainer.getMappedPort(SERVER_PORT));
    try (final CamundaClient camundaClient =
        new CamundaClientBuilderImpl()
            // set a longer timeout because containers in CI infrastructure can be slow
            .defaultRequestTimeout(Duration.ofSeconds(60))
            .preferRestOverGrpc(true)
            .restAddress(camundaEndpoint)
            .build()) {

      camundaClient
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .serviceTask("test")
                  .zeebeJobType("type")
                  .endEvent()
                  .done(),
              "test.bpmn")
          .send()
          .join();

      final var instance =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join();

      Awaitility.await("Process instance is visible via search")
          .atMost(Duration.ofMinutes(2))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var response = camundaClient.newProcessInstanceSearchRequest().send().join();
                assertThat(response.items()).hasSize(1);

                final var processInstance = response.items().getFirst();
                assertThat(processInstance.getProcessInstanceKey())
                    .isEqualTo(instance.getProcessInstanceKey());
              });
    }
  }
}
