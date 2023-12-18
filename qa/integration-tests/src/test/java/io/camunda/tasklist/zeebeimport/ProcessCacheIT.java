/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class ProcessCacheIT extends TasklistZeebeIntegrationTest {

  @SpyBean private ProcessCache processCache;

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @AfterEach
  public void after() {
    super.after();
    // clean the cache
    processCache.clearCache();
  }

  @Test
  public void testProcessDoesNotExist() {
    final String processName = processCache.getProcessName("2");
    assertThat(processName).isNull();
  }

  @Test
  public void testProcessNameAndTaskNameReturnedAndReused() {
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");

    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);

    String demoProcessName = processCache.getProcessName(processId1);
    assertThat(demoProcessName).isNotNull();

    // request task name, must be already in cache
    String taskName = processCache.getTaskName(processId1, "taskA");
    assertThat(taskName).isNotNull();
    // request once again, the cache should be used
    demoProcessName = processCache.getProcessName(processId1);
    assertThat(demoProcessName).isNotNull();
    taskName = processCache.getTaskName(processId1, "taskA");
    assertThat(taskName).isNotNull();

    verify(processCache, times(1)).putToCache(any(), any());
  }
}
