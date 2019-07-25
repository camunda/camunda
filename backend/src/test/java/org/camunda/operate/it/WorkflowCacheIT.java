/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;

import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class WorkflowCacheIT extends OperateZeebeIntegrationTest {

  @SpyBean
  private WorkflowCache workflowCache;
  
  @Autowired
  OperateTester tester;
  
  @Before
  @Override
  public void before() {
    super.before();
    tester.setZeebeClient(getClient());
  }

  @After
  public void after() {
    //clean the cache
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("cache"), new LinkedHashMap<>());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject cache into some of the beans");
    }
    super.after();
  }

  @Test
  public void testWorkflowDoesNotExist() {
    final String processNameDefault = workflowCache.getWorkflowNameOrDefaultValue(2L,"default_value");
    assertThat(processNameDefault).isEqualTo("default_value");
  }

  @Test
  public void testWorkflowVersionAndNameReturnedAndReused() {    
    Long workflowKey1 = tester
          .deployWorkflow("demoProcess_v_1.bpmn").waitUntil().workflowIsDeployed()
          .and().getWorkflowKey();
    
    tester
        .deployWorkflow("processWithGateway.bpmn").waitUntil().workflowIsDeployed();

    String demoProcessName = workflowCache.getWorkflowNameOrDefaultValue(workflowKey1,null);
    assertThat(demoProcessName).isNotNull();

    //request once again, the cache should be used
    demoProcessName = workflowCache.getWorkflowNameOrDefaultValue(workflowKey1,null);
    assertThat(demoProcessName).isNotNull();

    verify(workflowCache, times(1)).putToCache(any(), any());
  }

}
