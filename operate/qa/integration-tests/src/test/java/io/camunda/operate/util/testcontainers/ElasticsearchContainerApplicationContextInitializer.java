/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.testcontainers;

import java.util.Map;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ElasticsearchContainerApplicationContextInitializer
    extends AbstractContainerApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(getDockerImageName("testcontainers.elasticsearch"))
          .withEnv(
              Map.of(
                  "xpack.security.enabled", "true",
                  "ELASTIC_PASSWORD", "changeme"))
          .withExposedPorts(9200);

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    elasticsearch.start();
    final String elsUrl =
        String.format("http://%s:%d/", elasticsearch.getHost(), elasticsearch.getFirstMappedPort());
    TestPropertyValues.of(
            "camunda.operate.elasticsearch.url=" + elsUrl,
            "camunda.operate.elasticsearch.username=elastic",
            "camunda.operate.elasticsearch.password=changeme",
            "camunda.operate.elasticsearch.clusterName=docker-cluster",
            "camunda.operate.zeebeElasticsearch.url=" + elsUrl,
            "camunda.operate.zeebeElasticsearch.username=elastic",
            "camunda.operate.zeebeElasticsearch.password=changeme",
            "camunda.operate.zeebeElasticsearch.clusterName=docker-cluster",
            "camunda.operate.zeebeElasticsearch.prefix=zeebe-record")
        .applyTo(applicationContext.getEnvironment());
  }
}
