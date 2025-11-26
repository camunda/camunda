/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.testcontainers;

import java.util.Map;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class OpensearchContainerApplicationContextInitializer
    extends AbstractContainerApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static final OpenSearchContainer<?> OPENSEARCH =
      new OpenSearchContainer<>(getDockerImageName("testcontainers.opensearch"))
          .withEnv(Map.of())
          .withExposedPorts(9200);

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    OPENSEARCH.start();
    final String osUrl =
        String.format("http://%s:%d/", OPENSEARCH.getHost(), OPENSEARCH.getFirstMappedPort());
    TestPropertyValues.of(
            "camunda.operate.opensearch.url=" + osUrl,
            "camunda.operate.opensearch.username=elastic",
            "camunda.operate.opensearch.password=changeme",
            "camunda.operate.opensearch.clusterName=docker-cluster",
            "camunda.operate.zeebeOpensearch.url=" + osUrl,
            "camunda.operate.zeebeOpensearch.username=elastic",
            "camunda.operate.zeebeOpensearch.password=changeme",
            "camunda.operate.zeebeOpensearch.clusterName=docker-cluster",
            "camunda.operate.zeebeOpensearch.prefix=zeebe-record")
        .applyTo(applicationContext.getEnvironment());
  }
}
