/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.testcontainers;

import io.camunda.operate.conditions.DatabaseInfo;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ContainerApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override public void initialize(ConfigurableApplicationContext applicationContext) {
    switch(DatabaseInfo.getCurrent()) {
      case Elasticsearch -> new ElasticsearchContainerApplicationContextInitializer().initialize(applicationContext);
      case Opensearch -> new OpensearchContainerApplicationContextInitializer().initialize(applicationContext);
      default -> throw new IllegalArgumentException("Unsupported database " + DatabaseInfo.getCurrent());
    }
  }
}
