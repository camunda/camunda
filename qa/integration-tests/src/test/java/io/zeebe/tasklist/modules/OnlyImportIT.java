/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.modules;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.tasklist.ImportModuleConfiguration;
import io.zeebe.tasklist.WebappModuleConfiguration;
import io.zeebe.tasklist.property.TasklistProperties;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      TasklistProperties.PREFIX + ".webappEnabled = false",
      TasklistProperties.PREFIX + ".archiverEnabled = false"
    })
public class OnlyImportIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testWebappModuleIsNotPresent() {
    applicationContext.getBean(WebappModuleConfiguration.class);
  }
}
