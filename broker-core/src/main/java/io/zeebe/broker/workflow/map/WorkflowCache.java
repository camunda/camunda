/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.map;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.workflow.deployment.data.DeploymentRecord;
import io.zeebe.broker.workflow.deployment.data.DeploymentResource;
import io.zeebe.broker.workflow.deployment.data.Workflow;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.io.DirectBufferInputStream;

public class WorkflowCache {

  private final BpmnTransformer transformer = new BpmnTransformer();

  private final LongHashSet deployments = new LongHashSet();
  private final Long2ObjectHashMap<DeployedWorkflow> workflowsByKey = new Long2ObjectHashMap<>();
  private final Map<DirectBuffer, Int2ObjectHashMap<DeployedWorkflow>>
      workflowsByProcessIdAndVersion = new HashMap<>();
  private final Map<DirectBuffer, DeployedWorkflow> latestWorkflowsByProcessId = new HashMap<>();

  public boolean addWorkflow(final long deploymentKey, final DeploymentRecord deploymentRecord) {
    final boolean isNewDeployment = !deployments.contains(deploymentKey);
    if (isNewDeployment) {
      deployments.add(deploymentKey);
      for (final Workflow workflow : deploymentRecord.workflows()) {
        final long key = workflow.getKey();

        final DirectBuffer resourceName = workflow.getResourceName();
        for (final DeploymentResource resource : deploymentRecord.resources()) {
          if (resource.getResourceName().equals(resourceName)) {
            addWorkflowToCache(workflow, key, resource);
          }
        }
      }
    }
    return isNewDeployment;
  }

  private void addWorkflowToCache(
      final Workflow workflow, final long key, final DeploymentResource resource) {

    final int version = workflow.getVersion();

    final DirectBuffer resourceName = BufferUtil.cloneBuffer(resource.getResourceName());
    final DirectBuffer bpmnProcessId = BufferUtil.cloneBuffer(workflow.getBpmnProcessId());
    final DirectBuffer bpmnXml = BufferUtil.cloneBuffer(resource.getResource());

    Loggers.WORKFLOW_REPOSITORY_LOGGER.trace(
        "Workflow {} with version {} added", bpmnProcessId, version);
    // TODO: pull these things apart
    // TODO: may wanna catch exceptions
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new DirectBufferInputStream(bpmnXml));

    // TODO: do design time and runtime validation

    final List<ExecutableWorkflow> definitions = transformer.transformDefinitions(modelInstance);

    final ExecutableWorkflow executableWorkflow =
        definitions
            .stream()
            .filter((w) -> BufferUtil.equals(bpmnProcessId, w.getId()))
            .findFirst()
            .get();

    final DeployedWorkflow deployedWorkflow =
        new DeployedWorkflow(
            executableWorkflow, key, version, bpmnXml, resourceName, bpmnProcessId);

    addDeployedWorkflowToState(key, deployedWorkflow, version, bpmnProcessId);
  }

  private void addDeployedWorkflowToState(
      final long key,
      final DeployedWorkflow deployedWorkflow,
      final int version,
      final DirectBuffer bpmnProcessId) {
    workflowsByKey.put(key, deployedWorkflow);

    Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (versionMap == null) {
      versionMap = new Int2ObjectHashMap<>();
      workflowsByProcessIdAndVersion.put(bpmnProcessId, versionMap);
    }

    versionMap.put(version, deployedWorkflow);

    final DeployedWorkflow latestVersion = latestWorkflowsByProcessId.get(bpmnProcessId);
    if (latestVersion == null || latestVersion.getVersion() < version) {
      latestWorkflowsByProcessId.put(bpmnProcessId, deployedWorkflow);
    }
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(final DirectBuffer processId) {
    return latestWorkflowsByProcessId.get(processId);
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      final DirectBuffer processId, final int version) {
    final Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(processId);

    if (versionMap != null) {
      return versionMap.get(version);
    } else {
      return null;
    }
  }

  public DeployedWorkflow getWorkflowByKey(final long key) {
    return workflowsByKey.get(key);
  }

  public Collection<DeployedWorkflow> getWorkflows() {
    return workflowsByKey.values();
  }

  public Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    final Int2ObjectHashMap<DeployedWorkflow> workflowsByVersions =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (workflowsByVersions != null) {
      return workflowsByVersions.values();
    }
    return null;
  }
}
