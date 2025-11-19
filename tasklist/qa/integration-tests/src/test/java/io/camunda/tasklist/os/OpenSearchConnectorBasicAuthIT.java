/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestOpenSearchSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestOpenSearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {OpenSearchConnectorBasicAuthIT.OpenSearchStarter.class})
public class OpenSearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  private static final OpenSearchContainer OPENSEARCH_CONTAINER =
      new OpenSearchContainer("opensearchproject/opensearch:2.17.0");

  @Autowired
  @Qualifier("tasklistOsClient")
  OpenSearchClient openSearchClient;

  @BeforeAll
  static void beforeAll() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @AfterAll
  static void afterAll() {
    OPENSEARCH_CONTAINER.stop();
  }

  @Test
  public void canConnect() throws IOException {
    assertThat(openSearchClient).isNotNull();
    assertThat(openSearchClient.cluster().health().status()).isSameAs(HealthStatus.Green);
  }

  static class OpenSearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      OPENSEARCH_CONTAINER.start();

      final String osUrl =
          String.format(
              "http://%s:%s",
              OPENSEARCH_CONTAINER.getHost(), OPENSEARCH_CONTAINER.getMappedPort(9200));

      TestPropertyValues.of(
              "camunda.database.type=opensearch",
              "camunda.data.secondary-storage.type=opensearch",
              "camunda.operate.database=opensearch",
              "camunda.tasklist.database=opensearch",
              "zeebe.broker.exporters.camundaexporter.args.connect.type=opensearch",
              "camunda.database.url=" + osUrl,
              "camunda.data.secondary-storage.opensearch.url=" + osUrl,
              "camunda.operate.opensearch.url=" + osUrl,
              "camunda.tasklist.opensearch.url=" + osUrl,
              "camunda.database.opensearch.username=" + OPENSEARCH_CONTAINER.getUsername(),
              "camunda.database.opensearch.password=" + OPENSEARCH_CONTAINER.getPassword(),
              "camunda.data.secondary-storage.opensearch.cluster-name=docker-cluster")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
