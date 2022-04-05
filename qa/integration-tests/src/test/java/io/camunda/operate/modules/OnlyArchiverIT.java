/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.modules;

import io.camunda.operate.ArchiverModuleConfiguration;
import io.camunda.operate.ImportModuleConfiguration;
import io.camunda.operate.WebappModuleConfiguration;
import io.camunda.operate.property.OperateProperties;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {OperateProperties.PREFIX + ".importerEnabled = false",
    OperateProperties.PREFIX + ".webappEnabled = false"})
public class OnlyArchiverIT extends ModuleIntegrationTest {

  @Test
  public void testArchiverModuleIsPresent() {
    assertThat(applicationContext.getBean(ArchiverModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testImportModuleIsNotPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testWebappModuleIsNotPresent() {
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
  }
}