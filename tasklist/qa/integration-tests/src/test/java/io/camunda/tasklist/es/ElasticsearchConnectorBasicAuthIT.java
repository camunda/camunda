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

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
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
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {ElasticsearchConnectorBasicAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION)
          .withEnv(
              Map.of(
                  "xpack.security.enabled", "true",
                  "ELASTIC_PASSWORD", "changeme"
                  //        "xpack.security.transport.ssl.enabled","true",
                  //        "xpack.security.http.ssl.enabled", "true",
                  //        "xpack.security.transport.ssl.verification_mode","none",//"certificate",
                  //        "xpack.security.transport.ssl.keystore.path",
                  // "elastic-certificates.p12",
                  //        "xpack.security.transport.ssl.truststore.path",
                  // "elastic-certificates.p12"
                  ))
          .withExposedPorts(9200);

  @Autowired RestHighLevelClient tasklistEsClient;

  @Autowired RestHighLevelClient tasklistZeebeEsClient;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void canConnect() {
    assertThat(tasklistEsClient).isNotNull();
    assertThat(tasklistZeebeEsClient).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      elasticsearch.start();
      final String elsUrl = String.format("http://%s", elasticsearch.getHttpHostAddress());
      TestPropertyValues.of(
              // "camunda.tasklist.elasticsearch.host="+elasticsearch.getHost(),
              // "camunda.tasklist.elasticsearch.port="+elasticsearch.getFirstMappedPort(),
              "camunda.tasklist.elasticsearch.username=elastic",
              "camunda.tasklist.elasticsearch.password=changeme",
              "camunda.tasklist.elasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.elasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.username=elastic",
              "camunda.tasklist.zeebeElasticsearch.password=changeme",
              "camunda.tasklist.zeebeElasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeElasticsearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
