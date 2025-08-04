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
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import java.io.File;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
@Disabled
public class ElasticsearchConnectorSSLAuthIT extends TasklistIntegrationTest {

  static String certDir = new File("src/test/resources/certs").getAbsolutePath();

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION)
          .withCopyFileToContainer(
              MountableFile.forHostPath("src/test/resources/certs/elastic-stack-ca.p12"),
              "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
          // .withCopyFileToContainer(MountableFile.forClasspathResource("/certs/elastic-stack-ca.p12"),"/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
          .withPassword("elastic")
          .withEnv(
              Map.of(
                  "xpack.security.enabled",
                  "true",
                  "xpack.security.http.ssl.enabled",
                  "true",
                  "xpack.security.http.ssl.keystore.path",
                  "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12"))
          .withExposedPorts(9200)
          .waitingFor(Wait.forHttps("/").withBasicCredentials("elastic", "elastic"));

  @Autowired RestHighLevelClient tasklistEsClient;

  @Autowired RestHighLevelClient tasklistZeebeEsClient;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Disabled("Can be tested manually")
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

      final String elsUrl =
          String.format(
              "https://%s:%d/", elasticsearch.getHost(), elasticsearch.getFirstMappedPort());
      TestPropertyValues.of(
              "camunda.tasklist.elasticsearch.url=" + elsUrl,
              "camunda.tasklist.elasticsearch.username=elastic",
              "camunda.tasklist.elasticsearch.password=elastic",
              "camunda.tasklist.elasticsearch.clusterName=docker-cluster",
              // "camunda.tasklist.elasticsearch.ssl.certificatePath="+certDir+"/elastic-stack-ca.p12",
              // "camunda.tasklist.elasticsearch.ssl.selfSigned=true",
              // "camunda.tasklist.elasticsearch.ssl.verifyHostname=true",
              "camunda.tasklist.zeebeElasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.username=elastic",
              "camunda.tasklist.zeebeElasticsearch.password=elastic",
              // "camunda.tasklist.zeebeElasticsearch.ssl.certificatePath="+certDir+"/elastic-stack-ca.p12",
              // "camunda.tasklist.zeebeElasticsearch.ssl.selfSigned=true",
              // "camunda.tasklist.zeebeElasticsearch.ssl.verifyHostname=true",
              "camunda.tasklist.zeebeElasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeElasticsearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
