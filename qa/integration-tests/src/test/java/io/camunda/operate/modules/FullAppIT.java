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
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class FullAppIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
    assertThat(applicationContext.getBean(ArchiverModuleConfiguration.class)).isNotNull();
  }

}
