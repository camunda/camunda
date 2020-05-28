/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.modules;

import org.junit.Test;
import io.zeebe.tasklist.ImportModuleConfiguration;
import io.zeebe.tasklist.WebappModuleConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

public class FullAppIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
  }

}
