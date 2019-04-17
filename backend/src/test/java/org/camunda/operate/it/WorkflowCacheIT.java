/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.LinkedHashMap;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.boot.test.mock.mockito.SpyBean;
import io.zeebe.client.ZeebeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WorkflowCacheIT extends OperateZeebeIntegrationTest {

  private ZeebeClient zeebeClient;

  @SpyBean
  private WorkflowCache workflowCache;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
  }

  @After
  public void after() {
    super.after();
    //clean the cache
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("cache"), new LinkedHashMap<>());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject cache into some of the beans");
    }
  }

  @Test
  public void testWorkflowDoesNotExist() {
    final String demoProcessName = workflowCache.getWorkflowName("1");
    assertThat(demoProcessName).isNull();

    final Integer demoProcessVersion = workflowCache.getWorkflowVersion("1");
    assertThat(demoProcessVersion).isNull();
  }

  @Test
  public void testWorkflowVersionAndNameReturnedAndReused() {
    ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    ZeebeTestUtil.deployWorkflow(zeebeClient, "processWithGateway.bpmn");

    elasticsearchTestRule.processAllEvents(2);

    String demoProcessName = workflowCache.getWorkflowName("1");
    assertThat(demoProcessName).isNotNull();

    //request once again, the cache should be used
    demoProcessName = workflowCache.getWorkflowName("1");
    assertThat(demoProcessName).isNotNull();

    Integer demoProcessVersion = workflowCache.getWorkflowVersion("1");
    assertThat(demoProcessVersion).isNotNull();

    demoProcessVersion = workflowCache.getWorkflowVersion("1");
    assertThat(demoProcessVersion).isNotNull();

    verify(workflowCache, times(1)).putToCache(any(), any());
  }

}
