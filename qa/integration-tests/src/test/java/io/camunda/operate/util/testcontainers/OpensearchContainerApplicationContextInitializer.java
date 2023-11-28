/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.testcontainers;

import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class OpensearchContainerApplicationContextInitializer extends AbstractContainerApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static OpensearchContainer<?> opensearch =
    new OpensearchContainer<>(getDockerImageName("testcontainers.opensearch"))
      .withEnv(Map.of()).withExposedPorts(9200);

  @Override public void initialize(ConfigurableApplicationContext applicationContext) {
    opensearch.start();
    String osUrl = String.format("http://%s:%d/", opensearch.getHost(), opensearch.getFirstMappedPort());
    TestPropertyValues.of(
      "camunda.operate.opensearch.url=" + osUrl,
      "camunda.operate.opensearch.username=elastic",
      "camunda.operate.opensearch.password=changeme",
      "camunda.operate.opensearch.clusterName=docker-cluster",
      "camunda.operate.zeebeOpensearch.url="+ osUrl,
      "camunda.operate.zeebeOpensearch.username=elastic",
      "camunda.operate.zeebeOpensearch.password=changeme",
      "camunda.operate.zeebeOpensearch.clusterName=docker-cluster",
      "camunda.operate.zeebeOpensearch.prefix=zeebe-record"
    ).applyTo(applicationContext.getEnvironment());
  }
}
