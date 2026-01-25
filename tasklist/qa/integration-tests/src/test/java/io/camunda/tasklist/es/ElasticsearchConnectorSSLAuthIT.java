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
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.MountableFile;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ElasticsearchConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    })
@ContextConfiguration(initializers = {ElasticsearchConnectorSSLAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorSSLAuthIT extends TasklistIntegrationTest {

  private static final String CERTIFICATE_RESOURCE_PATH = "certs/elastic-stack-ca.p12";

  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION)
          .withCopyFileToContainer(
              MountableFile.forClasspathResource(CERTIFICATE_RESOURCE_PATH),
              "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
          .withEnv(
              Map.of(
                  "xpack.security.enabled",
                  "true",
                  "xpack.security.http.ssl.enabled",
                  "true",
                  "xpack.security.http.ssl.keystore.path",
                  "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12"))
          .withExposedPorts(9200)
          .withPassword("elastic")
          .waitingFor(
              Wait.forHttps("/").allowInsecure().withBasicCredentials("elastic", "elastic"));

  @Autowired
  @Qualifier("tasklistEsClient")
  ElasticsearchClient esClient;

  @BeforeAll
  static void beforeAll() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void canConnect() {
    assertThat(esClient).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      ELASTICSEARCH_CONTAINER.start();

      final String elsUrl =
          String.format(
              "https://%s:%d/",
              ELASTICSEARCH_CONTAINER.getHost(), ELASTICSEARCH_CONTAINER.getFirstMappedPort());

      TestPropertyValues.of(
              "camunda.data.secondary-storage.type=elasticsearch",
              "camunda.database.type=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "zeebe.broker.exporters.camundaexporter.args.connect.type=elasticsearch",
              "camunda.database.url=" + elsUrl,
              "camunda.data.secondary-storage.elasticsearch.url=" + elsUrl,
              "camunda.operate.elasticsearch.url=" + elsUrl,
              "camunda.tasklist.elasticsearch.url=" + elsUrl,
              "zeebe.broker.exporters.camundaexporter.args.connect.url=" + elsUrl,
              "camunda.data.secondary-storage.elasticsearch.username=elastic",
              "camunda.data.secondary-storage.elasticsearch.password=elastic",
              "camunda.data.secondary-storage.elasticsearch.security.certificate-path="
                  + getClass().getClassLoader().getResource(CERTIFICATE_RESOURCE_PATH).getPath(),
              "camunda.data.secondary-storage.elasticsearch.security.self-signed=true",
              "camunda.data.secondary-storage.elasticsearch.security.verify-hostname=false")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
