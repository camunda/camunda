/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.management.ElsIndicesHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      ElsIndicesHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryElasticsearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {ElasticsearchConnectorBasicAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.16.3")
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

  @Autowired RestHighLevelClient esClient;

  @Autowired RestHighLevelClient zeebeEsClient;

  @Test
  public void canConnect() {
    assertThat(esClient).isNotNull();
    assertThat(zeebeEsClient).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      elasticsearch.start();
      final String elsUrl = String.format("http://%s", elasticsearch.getHttpHostAddress());
      TestPropertyValues.of(
              // "camunda.tasklist.elasticsearch.host="+elasticsearch.getHost(),
              // "camunda.tasklist.elasticsearch.port="+elasticsearch.getFirstMappedPort(),
              "camunda.tasklist.elasticsearch.username=elastic",
              "camunda.tasklist.elasticsearch.password=changeme",
              "camunda.tasklist.elasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeElasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.username=elastic",
              "camunda.tasklist.zeebeElasticsearch.password=changeme",
              "camunda.tasklist.zeebeElasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeElasticsearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
