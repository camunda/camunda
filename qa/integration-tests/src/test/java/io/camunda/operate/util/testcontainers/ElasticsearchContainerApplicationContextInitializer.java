/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.testcontainers;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.Map;

public class ElasticsearchContainerApplicationContextInitializer extends AbstractContainerApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static ElasticsearchContainer elasticsearch =
    new ElasticsearchContainer(getDockerImageName("testcontainers.elasticsearch"))
      .withEnv(Map.of(
        "xpack.security.enabled", "true",
        "ELASTIC_PASSWORD","changeme"
      )).withExposedPorts(9200);

  @Override public void initialize(ConfigurableApplicationContext applicationContext) {
    elasticsearch.start();
    String elsUrl = String.format("http://%s:%d/", elasticsearch.getHost(), elasticsearch.getFirstMappedPort());
    TestPropertyValues.of(
      "camunda.operate.elasticsearch.url=" + elsUrl,
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
