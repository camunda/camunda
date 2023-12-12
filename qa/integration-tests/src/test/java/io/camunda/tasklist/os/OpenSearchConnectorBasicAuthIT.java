/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestOpenSearchSchemaManager;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestUtil;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.util.Map;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestOpenSearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryOpenSearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {OpenSearchConnectorBasicAuthIT.OpenSearchStarter.class})
public class OpenSearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  static OpensearchContainer opensearch =
      (OpensearchContainer)
          new OpensearchContainer("opensearchproject/opensearch:2.9.0")
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
  @Qualifier("openSearchClient")
  OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("zeebeOsClient")
  OpenSearchClient zeebeOsClient;

  @BeforeClass
  public static void beforeClass() {
    Assume.assumeTrue(TestUtil.isOpenSearch());
  }

  @Test
  public void canConnect() {
    assertThat(openSearchClient).isNotNull();
    assertThat(zeebeOsClient).isNotNull();
  }

  static class OpenSearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
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
