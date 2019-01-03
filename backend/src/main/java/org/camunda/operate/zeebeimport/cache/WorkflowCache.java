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
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.transformers.DeploymentEventTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.Workflows;

@Component
public class WorkflowCache {

  private LinkedHashMap<String, WorkflowEntity> cache = new LinkedHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  private WorkflowReader workflowReader;

  @Autowired
  private DeploymentEventTransformer deploymentEventTransformer;

  public WorkflowEntity getWorkflow(String workflowId, String bpmnProcessId) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData;
    } else {
      final WorkflowEntity newValue = findWorkflow(workflowId, bpmnProcessId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue;
      } else {
        return null;
      }
    }
  }

  public String getWorkflowName(String workflowId, String bpmnProcessId) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getName();
    } else {
      final WorkflowEntity newValue = findWorkflow(workflowId, bpmnProcessId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getName();
      } else {
        return null;
      }
    }
  }

  public Integer getWorkflowVersion(String workflowId, String bpmnProcessId) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getVersion();
    } else {
      final WorkflowEntity newValue = findWorkflow(workflowId, bpmnProcessId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getVersion();
      } else {
        return null;
      }
    }
  }

  private WorkflowEntity findWorkflow(String workflowId, String bpmnProcessId) {
    WorkflowEntity workflow = null;
    try {
      //find in Operate
      workflow = workflowReader.getWorkflow(workflowId);
    } catch (NotFoundException nfe) {
      //request from Zeebe
      final Workflows workflows = zeebeClient.newWorkflowRequest().bpmnProcessId(bpmnProcessId).send().join();
      for (Workflow workflowFromZeebe : workflows.getWorkflows()) {
        if (workflowFromZeebe.getWorkflowKey() == Long.valueOf(workflowId)) {
          //get BPMN XML
          final WorkflowResource workflowResource = zeebeClient.newResourceRequest().workflowKey(workflowFromZeebe.getWorkflowKey()).send().join();
          workflow = deploymentEventTransformer.extractDiagramData(workflowResource.getBpmnXmlAsStream());
          workflow.setKey(workflowFromZeebe.getWorkflowKey());
          workflow.setVersion(workflowFromZeebe.getVersion());
          workflow.setBpmnProcessId(workflowFromZeebe.getBpmnProcessId());
          workflow.setId(workflowId);
        }
      }
    }
    return workflow;
  }

  public void putToCache(String workflowId, WorkflowEntity workflow) {
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

  public void clearCache() {
    cache.clear();
  }

}
