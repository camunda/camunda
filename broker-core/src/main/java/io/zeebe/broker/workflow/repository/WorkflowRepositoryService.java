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
package io.zeebe.broker.workflow.repository;

import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadata;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import io.zeebe.servicecontainer.Service;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class WorkflowRepositoryService implements Service<WorkflowRepositoryService> {
  private final ActorControl actor;
  private final WorkflowState workflowState;

  public WorkflowRepositoryService(final ActorControl actor, final WorkflowState workflowState) {
    this.actor = actor;
    this.workflowState = workflowState;
  }

  @Override
  public WorkflowRepositoryService get() {
    return this;
  }

  public ActorFuture<WorkflowMetadataAndResource> getWorkflowByKey(final long key) {
    return actor.call(
        () -> {
          final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(key);

          if (deployedWorkflow == null) {
            return null;
          }

          return extractWorkflowMetadataAndResource(deployedWorkflow);
        });
  }

  public ActorFuture<WorkflowMetadataAndResource> getLatestWorkflowByBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    return actor.call(
        () -> {
          final DeployedWorkflow deployedWorkflow =
              workflowState.getLatestWorkflowVersionByProcessId(bpmnProcessId);
          if (deployedWorkflow == null) {
            return null;
          }

          return extractWorkflowMetadataAndResource(deployedWorkflow);
        });
  }

  public ActorFuture<WorkflowMetadataAndResource> getWorkflowByBpmnProcessIdAndVersion(
      final DirectBuffer bpmnProcessId, final int version) {
    return actor.call(
        () -> {
          final DeployedWorkflow deployedWorkflow =
              workflowState.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
          if (deployedWorkflow == null) {
            return null;
          }

          return extractWorkflowMetadataAndResource(deployedWorkflow);
        });
  }

  public ActorFuture<List<WorkflowMetadata>> getWorkflows() {
    return actor.call(
        () -> {
          final Collection<DeployedWorkflow> workflows = workflowState.getWorkflows();

          if (workflows == null || workflows.isEmpty()) {
            return null;
          }

          return createMetadataList(workflows);
        });
  }

  public ActorFuture<List<WorkflowMetadata>> getWorkflowsByBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    return actor.call(
        () -> {
          final Collection<DeployedWorkflow> workflows =
              workflowState.getWorkflowsByBpmnProcessId(bpmnProcessId);

          if (workflows == null || workflows.isEmpty()) {
            return null;
          }

          return createMetadataList(workflows);
        });
  }

  private List<WorkflowMetadata> createMetadataList(final Collection<DeployedWorkflow> workflows) {
    return workflows
        .stream()
        .map(
            workflow ->
                new WorkflowMetadata()
                    .setResourceName(workflow.getResourceName())
                    .setBpmnProcessId(workflow.getBpmnProcessId())
                    .setWorkflowKey(workflow.getKey())
                    .setVersion(workflow.getVersion()))
        .collect(Collectors.toList());
  }

  private WorkflowMetadataAndResource extractWorkflowMetadataAndResource(
      final DeployedWorkflow deployedWorkflow) {
    final int version = deployedWorkflow.getVersion();
    final long workflowKey = deployedWorkflow.getKey();
    final DirectBuffer deployment = deployedWorkflow.getResource();
    final DirectBuffer bpmnProcessId = deployedWorkflow.getBpmnProcessId();
    final DirectBuffer resourceName = deployedWorkflow.getResourceName();

    final WorkflowMetadataAndResource workflowMetadataAndResource =
        new WorkflowMetadataAndResource();

    workflowMetadataAndResource
        .setBpmnXml(deployment)
        .setVersion(version)
        .setWorkflowKey(workflowKey)
        .setBpmnProcessId(bpmnProcessId)
        .setResourceName(resourceName);

    return workflowMetadataAndResource;
  }
}
