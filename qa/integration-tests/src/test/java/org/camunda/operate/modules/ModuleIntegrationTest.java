/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.modules;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.DependencyInjectionTestExecutionListener;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.apps.modules.ModulesTestApplication;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ModulesTestApplication.class }, properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false"})
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class ModuleIntegrationTest {

  @Autowired
  protected ApplicationContext applicationContext;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

}
