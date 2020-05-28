/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.modules;

import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.apps.modules.ModulesTestApplication;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ModulesTestApplication.class },
    properties = { TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
    "graphql.servlet.websocket.enabled=false"})
public abstract class ModuleIntegrationTest {

  @Autowired
  protected ApplicationContext applicationContext;

}
