/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {ElasticsearchConnectorBasicAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION)
          .withEnv(Map.of("xpack.security.enabled", "true"))
          .withPassword("elastic")
          .withExposedPorts(9200);

  @Autowired ElasticsearchClient es8Client;

  @BeforeAll
  static void beforeAll() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @AfterAll
  static void afterAll() {
    ELASTICSEARCH_CONTAINER.stop();
  }

  @Test
  public void canConnect() {
    assertThat(es8Client).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      ELASTICSEARCH_CONTAINER.start();

      final String elsUrl =
          String.format("http://%s", ELASTICSEARCH_CONTAINER.getHttpHostAddress());

      TestPropertyValues.of(
              "camunda.database.type=elasticsearch",
              "camunda.data.secondary-storage.type=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              "zeebe.broker.exporters.camundaexporter.args.connect.type=elasticsearch",
              "camunda.data.secondary-storage.elasticsearch.url=" + elsUrl,
              "camunda.database.url=" + elsUrl,
              "camunda.operate.elasticsearch.url=" + elsUrl,
              "camunda.tasklist.elasticsearch.url=" + elsUrl,
              "zeebe.broker.exporters.camundaexporter.args.connect.url=" + elsUrl,
              "camunda.data.secondary-storage.elasticsearch.username=elastic",
              "camunda.data.secondary-storage.elasticsearch.password=elastic")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
