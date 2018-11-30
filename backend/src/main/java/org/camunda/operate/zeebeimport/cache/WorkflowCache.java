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
package org.camunda.operate.zeebeimport.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.zeebeimport.transformers.DeploymentEventTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.Workflows;

@Component
public class WorkflowCache {

  private LinkedHashMap<String, WorkflowData> cache = new LinkedHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  private WorkflowReader workflowReader;

  @Autowired
  private DeploymentEventTransformer deploymentEventTransformer;

  public String getWorkflowName(String workflowId, String bpmnProcessId) {
    final WorkflowData cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getName();
    } else {
      final WorkflowData newValue = findWorkflow(workflowId, bpmnProcessId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getName();
      } else {
        return null;
      }
    }
  }

  public Integer getWorkflowVersion(String workflowId, String bpmnProcessId) {
    final WorkflowData cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getVersion();
    } else {
      final WorkflowData newValue = findWorkflow(workflowId, bpmnProcessId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getVersion();
      } else {
        return null;
      }
    }
  }

  private WorkflowData findWorkflow(String workflowId, String bpmnProcessId) {
    WorkflowData workflow = null;
    try {
      //find in Operate
      final WorkflowEntity workflowEntity = workflowReader.getWorkflow(workflowId);
      workflow = new WorkflowData(Long.valueOf(workflowEntity.getId()), workflowEntity.getName(), workflowEntity.getVersion());
    } catch (NotFoundException nfe) {
      //request from Zeebe
      final Workflows workflows = zeebeClient.workflowClient().newWorkflowRequest().bpmnProcessId(bpmnProcessId).send().join();
      for (Workflow workflowFromZeebe : workflows.getWorkflows()) {
        if (workflowFromZeebe.getWorkflowKey() == Long.valueOf(workflowId)) {
          //get BPMN XML
          final WorkflowResource workflowResource = zeebeClient.workflowClient().newResourceRequest().workflowKey(workflowFromZeebe.getWorkflowKey()).send().join();
          String workflowName = deploymentEventTransformer.extractWorkflowName(workflowResource.getBpmnXmlAsStream());
          workflow = new WorkflowData(workflowFromZeebe.getWorkflowKey(), workflowName, workflowFromZeebe.getVersion());
        }
      }
    }
    return workflow;
  }

  public void putToCache(String workflowId, WorkflowData workflow) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      //remove 1st element
      final Iterator<String> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(workflowId, workflow);
  }

  public static class WorkflowData {
    private long key;
    private String name;
    private int version;

    public WorkflowData() {
    }

    public WorkflowData(long key, String name, int version) {
      this.key = key;
      this.name = name;
      this.version = version;
    }

    public long getKey() {
      return key;
    }

    public void setKey(long key) {
      this.key = key;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      WorkflowData that = (WorkflowData) o;

      if (key != that.key)
        return false;
      if (version != that.version)
        return false;
      return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
      int result = (int) (key ^ (key >>> 32));
      result = 31 * result + (name != null ? name.hashCode() : 0);
      result = 31 * result + version;
      return result;
    }
  }

}
