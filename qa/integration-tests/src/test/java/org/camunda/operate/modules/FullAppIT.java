/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.modules;

import org.camunda.operate.ImportModuleConfiguration;
import org.camunda.operate.WebappModuleConfiguration;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class FullAppIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
  }

}
