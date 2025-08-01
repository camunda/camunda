/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OpenSearchConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    })
@ContextConfiguration(initializers = {OpenSearchConnectorSSLAuthIT.ElasticsearchStarter.class})
@Profile("opensearch-test")
@Disabled
public class OpenSearchConnectorSSLAuthIT extends TasklistIntegrationTest {

  static String certDir = new File("src/test/resources/certs").getAbsolutePath();

  static OpensearchContainer opensearch =
      (OpensearchContainer)
          new OpensearchContainer("opensearchproject/opensearch:2.17.0")
              .withCopyFileToContainer(
                  MountableFile.forHostPath("src/test/resources/certs/elastic-stack-ca.p12"),
                  "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
              // .withCopyFileToContainer(MountableFile.forClasspathResource("/certs/elastic-stack-ca.p12"),"/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
              // .withPassword("elastic")
              .withEnv(
                  Map.of(
                      "xpack.security.http.ssl.keystore.path",
                      "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12"))
              .withExposedPorts(9200)
              .waitingFor(Wait.forHttps("/").withBasicCredentials("elastic", "elastic"));

  @Autowired
  @Qualifier("tasklistOsClient")
  OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("tasklistZeebeOsClient")
  OpenSearchClient zeebeOsClient;

  @Disabled("Can be tested manually")
  @Test
  public void canConnect() {
    assertThat(openSearchClient).isNotNull();
    assertThat(zeebeOsClient).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      opensearch.start();

      final String elsUrl =
          String.format("https://%s:%d/", opensearch.getHost(), opensearch.getFirstMappedPort());
      TestPropertyValues.of(
              "camunda.tasklist.opensearch.url=" + elsUrl,
              "camunda.tasklist.opensearch.username=elastic",
              "camunda.tasklist.opensearch.password=elastic",
              "camunda.tasklist.opensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.url=" + elsUrl,
              "camunda.tasklist.zeebeOpensearch.username=elastic",
              "camunda.tasklist.zeebeOpensearch.password=elastic",
              "camunda.tasklist.zeebeOpensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
