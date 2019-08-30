/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.modules;

import org.camunda.operate.ImportModuleConfiguration;
import org.camunda.operate.WebappModuleConfiguration;
import org.camunda.operate.property.OperateProperties;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = OperateProperties.PREFIX + ".webappEnabled = false")
public class OnlyImportIT extends ModuleIntegrationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testWebappModuleIsNotPresent() {
    applicationContext.getBean(WebappModuleConfiguration.class);
  }

}