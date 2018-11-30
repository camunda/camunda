/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @After
  public void after() {
    super.after();
    //clean the cache
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("cache"), new LinkedHashMap<>());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @Test
  public void testWorkflowDoesNotExist() {
    final String demoProcessName = workflowCache.getWorkflowName("1", "demoProcess");
    assertThat(demoProcessName).isNull();

    final Integer demoProcessVersion = workflowCache.getWorkflowVersion("1", "demoProcess");
    assertThat(demoProcessVersion).isNull();
  }

  @Test
  public void testWorkflowVersionAndNameReturnedAndReused() {
    ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    ZeebeTestUtil.deployWorkflow(zeebeClient, "processWithGateway.bpmn");


    String demoProcessName = workflowCache.getWorkflowName("1", "demoProcess");
    assertThat(demoProcessName).isNotNull();

    //request once again, the cache should be used
    demoProcessName = workflowCache.getWorkflowName("1", "demoProcess");
    assertThat(demoProcessName).isNotNull();

    Integer demoProcessVersion = workflowCache.getWorkflowVersion("1", "demoProcess");
    assertThat(demoProcessVersion).isNotNull();

    demoProcessVersion = workflowCache.getWorkflowVersion("1", "demoProcess");
    assertThat(demoProcessVersion).isNotNull();

    verify(workflowCache, times(1)).putToCache(any(), any());
  }

}
