/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.metric;

import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestImportListener;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class MetricWithMockedImporterIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private TestImportListener countImportListener;

  @MockBean
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Before
  public void before() {
    super.before();
    clearMetrics();
  }

  @Test
  public void testQueueSize() throws IOException {

    //executor is not executing anything, just holds a callable forever
    when(importExecutor.submit(any(Callable.class))).thenReturn(mock(Future.class));

    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .endEvent()
            .done();

    //process
    tester
        .deployProcess(startEndProcess, "startEndProcess.bpmn");
    waitTillSmthRecordsAreReadToQueue();    //this will be picked up by importExecutor

    //process instance partition 1
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue();    //this will be picked up by importExecutor

    //process instance partition 2
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue();    //this will be picked up by importExecutor

    //process instance partition 1
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue();    //this will end up in the queue for partition 1

    assertThatMetricsFrom(mockMvc,
        new MetricAssert.ValueMatcher("operate_import_queue_size{partition=\"1\",type=\"PROCESS_INSTANCE\",}",
            d -> d.doubleValue() >= 1.0));

  }

  private void waitTillSmthRecordsAreReadToQueue() throws IOException {
    countImportListener.resetCounters();
    while (countImportListener.getScheduledCount() == 0) {
      sleepFor(500);
      zeebeImporter.performOneRoundOfImport();
    }
  }

}
