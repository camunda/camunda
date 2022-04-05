/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.modules;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.modules.ModulesTestApplication;
import io.camunda.operate.webapp.security.UserService;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ModulesTestApplication.class },
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false"})
public abstract class ModuleIntegrationTest {

  @Autowired
  protected ApplicationContext applicationContext;

  @MockBean
  protected UserService userService;
}
