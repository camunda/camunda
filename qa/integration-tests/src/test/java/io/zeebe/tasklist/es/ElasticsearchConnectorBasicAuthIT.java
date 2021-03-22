/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
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
      TestApplicationWithNoBeans.class,
      TasklistProperties.class,
      ElasticsearchConnector.class
    })
@ContextConfiguration(initializers = {ElasticsearchConnectorBasicAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorBasicAuthIT {

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.8.13")
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
              // "zeebe.tasklist.elasticsearch.host="+elasticsearch.getHost(),
              // "zeebe.tasklist.elasticsearch.port="+elasticsearch.getFirstMappedPort(),
              "zeebe.tasklist.elasticsearch.username=elastic",
              "zeebe.tasklist.elasticsearch.password=changeme",
              "zeebe.tasklist.elasticsearch.clusterName=docker-cluster",
              "zeebe.tasklist.zeebeElasticsearch.url=" + elsUrl,
              "zeebe.tasklist.zeebeElasticsearch.username=elastic",
              "zeebe.tasklist.zeebeElasticsearch.password=changeme",
              "zeebe.tasklist.zeebeElasticsearch.clusterName=docker-cluster",
              "zeebe.tasklist.zeebeElasticsearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
