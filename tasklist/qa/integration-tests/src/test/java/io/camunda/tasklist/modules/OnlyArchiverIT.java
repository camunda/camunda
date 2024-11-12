/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.tasklist.ArchiverModuleConfiguration;
import io.camunda.tasklist.ImportModuleConfiguration;
import io.camunda.tasklist.WebappModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.controllers.TasklistIndexController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      TasklistProperties.PREFIX + ".importerEnabled = false",
      TasklistProperties.PREFIX + ".webappEnabled = false"
    })
public class OnlyArchiverIT extends ModuleIntegrationTest {

  @Test
  public void testArchiverModuleIsPresent() {
    assertThat(applicationContext.getBean(ArchiverModuleConfiguration.class)).isNotNull();
  }

  @Test
  public void testImportModuleIsNotPresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> applicationContext.getBean(ImportModuleConfiguration.class));
  }

  @Test
  public void testWebappModuleIsNotPresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> applicationContext.getBean(WebappModuleConfiguration.class));
  }

  @Test
  public void testTasklistIndexControllerIsNotPresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> applicationContext.getBean(TasklistIndexController.class));
  }
}
