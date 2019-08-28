/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.modules;

import org.camunda.operate.ImportModuleConfiguration;
import org.camunda.operate.WebappModuleConfiguration;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.modules.ModulesTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ModulesTestApplication.class }, properties = { OperateProperties.PREFIX + ".importProperties.startLoadingDataOnStartup = false",
    OperateProperties.PREFIX + ".webappEnabled = false" })
public class OnlyWebappIT {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  public void testImportModuleIsNotPresent() {
    applicationContext.getBean(ImportModuleConfiguration.class);
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testWebappModuleIsPresent() {
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
  }
}