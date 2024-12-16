/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.modules;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.ImportModuleConfiguration;
import io.camunda.tasklist.WebappModuleConfiguration;
import io.camunda.tasklist.webapp.controllers.TasklistIndexController;
import org.junit.jupiter.api.Test;

public class FullAppIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(TasklistIndexController.class)).isNotNull();
  }
}
