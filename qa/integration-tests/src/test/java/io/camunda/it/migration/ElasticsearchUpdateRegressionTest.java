/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.qa.util.multidb.ElasticOpenSearchSetupHelper;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchUpdateRegressionTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchUpdateRegressionTest.class);

  private static final Network NETWORK = Network.newNetwork();
  private static final String ELASTIC_ALIAS = "elasticsearch";

  @Container
  public static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(ELASTIC_ALIAS)
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI

  @BeforeAll
  public static void setup() {
    final var esUrl = String.format("http://%s:%d", ELASTIC_ALIAS, 9200);
    final GenericContainer<?> schemaManagerContainer =
        new GenericContainer<>("camunda/zeebe:8.7.0-SNAPSHOT")
            .withCreateContainerCmdModifier(
                (final CreateContainerCmd cmd) -> cmd.withEntrypoint("/usr/local/zeebe/bin/schema"))
            .withNetwork(NETWORK)
            .withNetworkAliases("camunda")
            .withEnv("CAMUNDA_DATABASE_URL", esUrl)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", esUrl);

    schemaManagerContainer.start();
    schemaManagerContainer.followOutput(new Slf4jLogConsumer(LOGGER));
  }

  @Test
  public void shouldBeAbleToUpdateAndExport() {
    // given
    final String httpHostAddress = "http://" + ELASTICSEARCH_CONTAINER.getHttpHostAddress();
    final var expectedDescriptors = new IndexDescriptors("", true).all();
    final var setupHelper = new ElasticOpenSearchSetupHelper(httpHostAddress, expectedDescriptors);

    Awaitility.await("Await schema readiness")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .until(() -> setupHelper.validateSchemaCreation(""));
    final TestStandaloneBroker testStandaloneBroker = new TestStandaloneBroker();
    final MultiDbConfigurator multiDbConfigurator = new MultiDbConfigurator(testStandaloneBroker);
    multiDbConfigurator.configureElasticsearchSupport(httpHostAddress, "");

    // when
    testStandaloneBroker.start();
    final CamundaClient camundaClient = testStandaloneBroker.newClientBuilder().build();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("test").startEvent().endEvent().done(), "test.bpmn")
        .send()
        .join();

    // then
    Awaitility.await("wait for processes")
        .untilAsserted(
            () -> {
              final SearchQueryResponse<ProcessDefinition> searchQueryResponse =
                  camundaClient.newProcessDefinitionQuery().send().join();
              assertThat(searchQueryResponse.items().size()).isNotZero();
            });
  }
}
