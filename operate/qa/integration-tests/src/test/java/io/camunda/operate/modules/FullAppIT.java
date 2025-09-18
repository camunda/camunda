/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.modules;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.ImportModuleConfiguration;
import io.camunda.operate.WebappModuleConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.controllers.OperateIndexController;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      OperateProperties.PREFIX + ".importer-enabled = true",
    })
public class FullAppIT extends ModuleAbstractIT {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(OperateIndexController.class)).isNotNull();
  }
}
