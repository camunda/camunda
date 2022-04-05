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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.File;
import java.util.Map;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplicationWithNoBeans.class, OperateProperties.class, ElasticsearchConnector.class}
)
@ContextConfiguration(initializers = { ElasticsearchConnectorSSLAuthIT.ElasticsearchStarter.class})
public class ElasticsearchConnectorSSLAuthIT {

  static String certDir = new File("src/test/resources/certs").getAbsolutePath();

  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.16.2")
          .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/certs/elastic-stack-ca.p12"),"/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
          //.withCopyFileToContainer(MountableFile.forClasspathResource("/certs/elastic-stack-ca.p12"),"/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12")
          .withPassword("elastic")
          .withEnv(Map.of(
          "xpack.security.enabled", "true",
          "xpack.security.http.ssl.enabled", "true",
          "xpack.security.http.ssl.keystore.path", "/usr/share/elasticsearch/config/certs/elastic-stack-ca.p12"
        )).withExposedPorts(9200)
          .waitingFor(
              Wait.forHttps("/")
                  .withBasicCredentials("elastic", "elastic"));

  @Autowired
  RestHighLevelClient esClient;

  @Autowired
  RestHighLevelClient zeebeEsClient;

  static class ElasticsearchStarter implements ApplicationContextInitializer<ConfigurableApplicationContext>{

    @Override public void initialize(ConfigurableApplicationContext applicationContext) {
      elasticsearch.start();

      String elsUrl = String.format("https://%s:%d/", elasticsearch.getHost(), elasticsearch.getFirstMappedPort());
      TestPropertyValues.of(
          "camunda.operate.elasticsearch.url=" + elsUrl,
          "camunda.operate.elasticsearch.username=elastic",
          "camunda.operate.elasticsearch.password=elastic",
          "camunda.operate.elasticsearch.clusterName=docker-cluster",
          //"camunda.operate.elasticsearch.ssl.certificatePath="+certDir+"/elastic-stack-ca.p12",
          //"camunda.operate.elasticsearch.ssl.selfSigned=true",
          //"camunda.operate.elasticsearch.ssl.verifyHostname=true",
          "camunda.operate.zeebeElasticsearch.url="+ elsUrl,
          "camunda.operate.zeebeElasticsearch.username=elastic",
          "camunda.operate.zeebeElasticsearch.password=elastic",
          //"camunda.operate.zeebeElasticsearch.ssl.certificatePath="+certDir+"/elastic-stack-ca.p12",
          //"camunda.operate.zeebeElasticsearch.ssl.selfSigned=true",
          //"camunda.operate.zeebeElasticsearch.ssl.verifyHostname=true",
          "camunda.operate.zeebeElasticsearch.clusterName=docker-cluster",
          "camunda.operate.zeebeElasticsearch.prefix=zeebe-record"
      ).applyTo(applicationContext.getEnvironment());
    }
  }

  @Ignore("Can be tested manually")
  @Test
  public void canConnect(){
    assertThat(esClient).isNotNull();
    assertThat(zeebeEsClient).isNotNull();
  }

}
