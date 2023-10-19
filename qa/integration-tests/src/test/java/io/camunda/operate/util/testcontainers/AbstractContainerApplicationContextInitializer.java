/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.testcontainers;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

public abstract class AbstractContainerApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  protected static String getDockerImageName(String testContainerPropertyName) {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("config/application-test.yml"));
    return yaml.getObject().getProperty(testContainerPropertyName);
  }
}
