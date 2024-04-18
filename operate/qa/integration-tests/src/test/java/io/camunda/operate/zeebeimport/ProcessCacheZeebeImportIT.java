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
        ZeebeTestUtil.deployProcess(zeebeClient, null, "demoProcess_v_1.bpmn");
    final Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "processWithGateway.bpmn");

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
        ZeebeTestUtil.deployProcess(zeebeClient, null, "demoProcess_v_1.bpmn");
    final Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "processWithGateway.bpmn");

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
}
