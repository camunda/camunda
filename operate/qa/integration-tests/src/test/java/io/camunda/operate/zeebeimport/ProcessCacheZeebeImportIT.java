/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class ProcessCacheZeebeImportIT extends OperateZeebeAbstractIT {

  @SpyBean private ProcessCache processCache;

  @Override
  @After
  public void after() {
    // clean the cache
    processCache.clearCache();
    super.after();
  }

  @Test
  public void testProcessDoesNotExist() {
    final String processNameDefault =
        processCache.getProcessNameOrDefaultValue(2L, "default_value");
    assertThat(processNameDefault).isEqualTo("default_value");
  }

  @Test
  public void testProcessVersionAndNameReturnedAndReused() {
    final Long processDefinitionKey1 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "demoProcess_v_1.bpmn");
    final Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "processWithGateway.bpmn");

    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1, null);
    assertThat(demoProcessName).isNotNull();

    // request once again, the cache should be used
    demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1, null);
    assertThat(demoProcessName).isNotNull();

    verify(processCache, times(1)).putToCache(any(), any());
  }

  @Test
  public void testProcessFlowNodeNameReturnedAndReused() {
    final Long processDefinitionKey1 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "demoProcess_v_1.bpmn");
    final Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "processWithGateway.bpmn");

    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String flowNodeName =
        processCache.getFlowNodeNameOrDefaultValue(processDefinitionKey1, "start", null);
    assertThat(flowNodeName).isEqualTo("start");

    // request once again, the cache should be used
    flowNodeName = processCache.getFlowNodeNameOrDefaultValue(processDefinitionKey1, "start", null);
    assertThat(flowNodeName).isEqualTo("start");

    verify(processCache, times(1)).putToCache(any(), any());
  }

  @Test
  public void testProcessVersionTagReturned() {
    // has versionTag
    final Long processDefinitionKey1 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "demoProcess_v_1.bpmn");
    // has no versionTag
    final Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(camundaClient, null, "processWithGateway.bpmn");

    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String demoProcessVersionTag = processCache.getProcessVersionTag(processDefinitionKey1);
    assertThat(demoProcessVersionTag).isEqualTo("demo-tag_v1");
    demoProcessVersionTag = processCache.getProcessVersionTag(processDefinitionKey2);
    assertThat(demoProcessVersionTag).isNull();
  }
}
