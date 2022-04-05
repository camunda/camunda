/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplicationWithNoBeans.class, OperateProperties.class, ElasticsearchConnector.class}
)
@ContextConfiguration(initializers = { ElasticsearchConnectorBasicAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorBasicAuthIT {

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.16.2")
          .withEnv(Map.of(
          "xpack.security.enabled", "true",
          "ELASTIC_PASSWORD","changeme"
//        "xpack.security.transport.ssl.enabled","true",
//        "xpack.security.http.ssl.enabled", "true",
//        "xpack.security.transport.ssl.verification_mode","none",//"certificate",
//        "xpack.security.transport.ssl.keystore.path", "elastic-certificates.p12",
//        "xpack.security.transport.ssl.truststore.path", "elastic-certificates.p12"
        )).withExposedPorts(9200);

  @Autowired
  RestHighLevelClient esClient;

  @Autowired
  RestHighLevelClient zeebeEsClient;

  static class ElasticsearchStarter implements ApplicationContextInitializer<ConfigurableApplicationContext>{

    @Override public void initialize(ConfigurableApplicationContext applicationContext) {
      elasticsearch.start();
      String elsUrl = String.format("http://%s:%d/", elasticsearch.getHost(), elasticsearch.getFirstMappedPort());
      TestPropertyValues.of(
          "camunda.operate.elasticsearch.url=" + elsUrl,
          //"camunda.operate.elasticsearch.host="+elasticsearch.getHost(),
          //"camunda.operate.elasticsearch.port="+elasticsearch.getFirstMappedPort(),
          "camunda.operate.elasticsearch.username=elastic",
          "camunda.operate.elasticsearch.password=changeme",
          "camunda.operate.elasticsearch.clusterName=docker-cluster",
          "camunda.operate.zeebeElasticsearch.url="+ elsUrl,
          "camunda.operate.zeebeElasticsearch.username=elastic",
          "camunda.operate.zeebeElasticsearch.password=changeme",
          "camunda.operate.zeebeElasticsearch.clusterName=docker-cluster",
          "camunda.operate.zeebeElasticsearch.prefix=zeebe-record"
      ).applyTo(applicationContext.getEnvironment());
    }
  }

  @Test
  public void canConnect(){
    assertThat(esClient).isNotNull();
    assertThat(zeebeEsClient).isNotNull();
  }

}
