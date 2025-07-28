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
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
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
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {OpenSearchConnectorBasicAuthIT.OpenSearchStarter.class})
public class OpenSearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  static OpensearchContainer opensearch =
      (OpensearchContainer)
          new OpensearchContainer("opensearchproject/opensearch:2.17.0")
              .withEnv(
                  Map.of(
                      // "plugins.security.disabled", "false",
                      "OPENSEARCH_PASSWORD", "changeme",
                      "plugins.security.allow_unsafe_democertificates", "true"
                      //        "xpack.security.transport.ssl.enabled","true",
                      //        "xpack.security.http.ssl.enabled", "true",
                      //
                      // "xpack.security.transport.ssl.verification_mode","none",//"certificate",
                      //        "xpack.security.transport.ssl.keystore.path",
                      // "elastic-certificates.p12",
                      //        "xpack.security.transport.ssl.truststore.path",
                      // "elastic-certificates.p12"
                      ))
              .withExposedPorts(9200, 9200);

  @Autowired
  @Qualifier("tasklistOsClient")
  OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("tasklistZeebeOsClient")
  OpenSearchClient zeebeOsClient;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Test
  public void canConnect() {
    assertThat(openSearchClient).isNotNull();
    assertThat(zeebeOsClient).isNotNull();
  }

  static class OpenSearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      opensearch.start();

      final String osUrl =
          String.format("http://%s:%s", opensearch.getHost(), opensearch.getMappedPort(9200));
      TestPropertyValues.of(
              "camunda.tasklist.opensearch.username=opensearch",
              "camunda.tasklist.opensearch.password=changeme",
              "camunda.tasklist.opensearch.url=" + osUrl,
              "camunda.tasklist.opensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.url=" + osUrl,
              "camunda.tasklist.zeebeOpensearch.username=opensearch",
              "camunda.tasklist.zeebeOpensearch.password=changeme",
              "camunda.tasklist.zeebeOpensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
