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
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class WorkflowCacheIT extends OperateZeebeIntegrationTest {

  @SpyBean
  private WorkflowCache workflowCache;

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
    Long workflowKey1 = ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    Long workflowKey2 = ZeebeTestUtil.deployWorkflow(zeebeClient, "processWithGateway.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey1);
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey2);

    String demoProcessName = workflowCache.getWorkflowNameOrDefaultValue(workflowKey1,null);
    assertThat(demoProcessName).isNotNull();

    //request once again, the cache should be used
    demoProcessName = workflowCache.getWorkflowNameOrDefaultValue(workflowKey1,null);
    assertThat(demoProcessName).isNotNull();

    verify(workflowCache, times(1)).putToCache(any(), any());
  }

}
