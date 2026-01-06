/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.testcontainers;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

public abstract class AbstractContainerApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  protected static String getDockerImageName(String testContainerPropertyName) {
    final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("config/application-test.yml"));
    return yaml.getObject().getProperty(testContainerPropertyName);
  }
}
