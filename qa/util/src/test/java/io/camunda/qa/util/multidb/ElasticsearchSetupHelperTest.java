/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ElasticsearchSetupHelperTest {

  private static ElasticsearchContainer elasticsearchContainer;
  private static String elasticSearchUrl;
  private ElasticOpenSearchSetupHelper elasticOpenSearchSetupHelper;
  private String indexPrefix;

  @BeforeAll
  public static void setUpEs() {
    elasticsearchContainer = TestSearchContainers.createDefeaultElasticsearchContainer();
    elasticsearchContainer.start();
    elasticSearchUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
  }

  @AfterAll
  public static void tearDownES() {
    elasticsearchContainer.close();
  }

  @Nested
  public class ConnectionCheck {
    @Test
    public void shouldValidateConnection() {
      // given
      elasticOpenSearchSetupHelper = new ElasticOpenSearchSetupHelper(elasticSearchUrl, List.of());

      // when
      final boolean connectionIsValid = elasticOpenSearchSetupHelper.validateConnection();

      // then
      assertThat(connectionIsValid).isTrue();
    }

    @Test
    public void shouldFailValidatingConnectionOnWrongURL() {
      // given
      final String wrongEndpoint = "http://wrongUrl";
      elasticOpenSearchSetupHelper = new ElasticOpenSearchSetupHelper(wrongEndpoint, List.of());

      // when
      final boolean connectionIsValid = elasticOpenSearchSetupHelper.validateConnection();

      // then
      assertThat(connectionIsValid).isFalse();
    }
  }

  @Nested
  public class DataCheck {

    private TestStandaloneBroker testApplication;
    private MultiDbConfigurator multiDbConfigurator;
    private CamundaClient camundaClient;

    @BeforeEach
    public void setup() {
      indexPrefix = UUID.randomUUID().toString().substring(0, 8);

      testApplication = new TestStandaloneBroker();
      multiDbConfigurator = new MultiDbConfigurator(testApplication);

      multiDbConfigurator.configureElasticsearchSupport(elasticSearchUrl, indexPrefix);
      final var expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
      elasticOpenSearchSetupHelper =
          new ElasticOpenSearchSetupHelper(elasticSearchUrl, expectedDescriptors);

      testApplication.start();
      testApplication.awaitCompleteTopology();

      camundaClient = testApplication.newClientBuilder().build();

      // to generate some data - especially for ES exporter
      camundaClient
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("test").startEvent().endEvent().done(), "process.bpmn")
          .send()
          .join();

      camundaClient.newCreateInstanceCommand().bpmnProcessId("test").latestVersion().send().join();
      camundaClient.newCreateInstanceCommand().bpmnProcessId("test").latestVersion().send().join();
      camundaClient.newCreateInstanceCommand().bpmnProcessId("test").latestVersion().send().join();
    }

    @Test
    public void shouldValidateSchema() {
      // given

      // when
      Awaitility.await("schema should be created eventually")
          .untilAsserted(
              () -> {
                final boolean schemaHasBeenCreated =
                    elasticOpenSearchSetupHelper.validateSchemaCreation(indexPrefix);

                // then
                assertThat(schemaHasBeenCreated).isTrue();
              });
    }

    @Test
    public void shouldFailValidatingSchemaWithWrongIndexPrefix() {
      // given

      // when
      final boolean schemaHasBeenCreated =
          elasticOpenSearchSetupHelper.validateSchemaCreation("wrongPrefix");

      // then
      assertThat(schemaHasBeenCreated).isFalse();
    }

    @Test
    public void shouldCleanupSchema() {
      // given
      Awaitility.await("schema should be created eventually")
          .untilAsserted(
              () -> {
                final boolean schemaHasBeenCreated =
                    elasticOpenSearchSetupHelper.validateSchemaCreation(indexPrefix);
                assertThat(schemaHasBeenCreated).isTrue();

                // We want to wait in our test whether ES Indices have been created, to validate
                // clean up works correctly. We need to check for different indices, as ES exporter
                // indices contain _ after prefix.
                //
                // We don't want to have this as part of the #validateSchemaCreation
                // as they are only created when data is exported. This would mean we need to
                // generate additional data, to trigger such, which we don't want for most tests
                final String esExporterPrefix = multiDbConfigurator.zeebeIndexPrefix() + "_";
                final int indexCount =
                    elasticOpenSearchSetupHelper.getCountOfIndicesWithPrefix(
                        elasticSearchUrl, esExporterPrefix);
                assertThat(indexCount).isNotZero();
                final int templateCount =
                    elasticOpenSearchSetupHelper.getCountOfIndexTemplatesWithPrefix(
                        elasticSearchUrl, esExporterPrefix);
                assertThat(templateCount).isNotZero();
              });
      // we need to make sure that the application stopped, before we start deleting
      // otherwise the test becomes flaky - as we run into race conditions
      testApplication.close();

      // when
      elasticOpenSearchSetupHelper.cleanup(indexPrefix);

      // then
      Awaitility.await("Indices should be cleaned up")
          .timeout(Duration.ofMinutes(1))
          .untilAsserted(
              () -> {
                final boolean schemaHasBeenCreated =
                    elasticOpenSearchSetupHelper.validateSchemaCreation(indexPrefix);
                assertThat(schemaHasBeenCreated).isFalse();

                final int countOfIndicesWithPrefix =
                    elasticOpenSearchSetupHelper.getCountOfIndicesWithPrefix(
                        elasticSearchUrl, indexPrefix);
                assertThat(countOfIndicesWithPrefix).isZero();

                final int countOfIndexTemplatesWithPrefix =
                    elasticOpenSearchSetupHelper.getCountOfIndexTemplatesWithPrefix(
                        elasticSearchUrl, indexPrefix);
                assertThat(countOfIndexTemplatesWithPrefix).isZero();
              });
    }

    @AfterEach
    public void tearDown() {
      elasticOpenSearchSetupHelper.close();
      testApplication.close();
    }
  }
}
