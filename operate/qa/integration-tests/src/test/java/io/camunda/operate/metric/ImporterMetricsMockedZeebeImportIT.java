/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.metric;

import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeAbstractIT;
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

public class ImporterMetricsMockedZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private ZeebeImporter zeebeImporter;

  @Autowired private TestImportListener countImportListener;

  @MockBean
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Override
  @Before
  public void before() {
    super.before();
    clearMetrics();
  }

  @Test
  public void testQueueSize() throws IOException {

    // executor is not executing anything, just holds a callable forever
    when(importExecutor.submit(any(Callable.class))).thenReturn(mock(Future.class));

    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();

    // process
    tester.deployProcess(startEndProcess, "startEndProcess.bpmn");
    waitTillSmthRecordsAreReadToQueue(); // this will be picked up by importExecutor

    // process instance partition 1
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue(); // this will be picked up by importExecutor

    // process instance partition 2
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue(); // this will be picked up by importExecutor

    // process instance partition 1
    tester.startProcessInstance(bpmnProcessId);
    waitTillSmthRecordsAreReadToQueue(); // this will end up in the queue for partition 1

    assertThatMetricsFrom(
        mockMvc,
        new MetricAssert.ValueMatcher(
            "operate_import_queue_size{partition=\"1\",type=\"PROCESS_INSTANCE\",}",
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
