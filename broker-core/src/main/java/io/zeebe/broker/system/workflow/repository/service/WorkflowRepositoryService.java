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
package io.zeebe.broker.system.workflow.repository.service;

import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex.WorkflowMetadata;
import io.zeebe.servicecontainer.Service;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import org.agrona.DirectBuffer;

public class WorkflowRepositoryService implements Service<WorkflowRepositoryService> {
  private final ActorControl actor;
  private final WorkflowRepositoryIndex index;
  private final DeploymentResourceCache resourceCache;

  public WorkflowRepositoryService(
      ActorControl actor,
      WorkflowRepositoryIndex index,
      DeploymentResourceCache deploymentWorkflowsCache) {
    this.actor = actor;
    this.index = index;
    this.resourceCache = deploymentWorkflowsCache;
  }

  @Override
  public WorkflowRepositoryService get() {
    return this;
  }

  public ActorFuture<Tuple<WorkflowMetadata, DirectBuffer>> getWorkflowByKey(long key) {
    return actor.call(
        () -> {
          final WorkflowMetadata metadata = index.getWorkflowByKey(key);

          if (metadata != null) {
            final DirectBuffer resource = resourceCache.getResource(metadata);

            return new Tuple<>(metadata, resource);
          }

          return null;
        });
  }

  public ActorFuture<Tuple<WorkflowMetadata, DirectBuffer>> getLatestWorkflowByBpmnProcessId(
      String topicName, String bpmnProcessId) {
    return actor.call(
        () -> {
          final WorkflowMetadata metadata =
              index.getLatestWorkflowByBpmnProcessId(topicName, bpmnProcessId);

          if (metadata != null) {
            final DirectBuffer resource = resourceCache.getResource(metadata);

            return new Tuple<>(metadata, resource);
          }

          return null;
        });
  }

  public ActorFuture<Tuple<WorkflowMetadata, DirectBuffer>> getWorkflowByBpmnProcessIdAndVersion(
      String topicName, String bpmnProcessId, int version) {
    return actor.call(
        () -> {
          final WorkflowMetadata metadata =
              index.getWorkflowByBpmnProcessIdAndVersion(topicName, bpmnProcessId, version);

          if (metadata != null) {
            final DirectBuffer resource = resourceCache.getResource(metadata);

            return new Tuple<>(metadata, resource);
          }

          return null;
        });
  }

  public ActorFuture<List<WorkflowMetadata>> getWorkflowsByTopic(String topicName) {
    return actor.call(
        () -> {
          return index.getWorkflowsByTopic(topicName);
        });
  }

  public ActorFuture<List<WorkflowMetadata>> getWorkflowsByBpmnProcessId(
      String topicName, String bpmnProcessId) {
    return actor.call(
        () -> {
          return index.getWorkflowsByTopicAndBpmnProcessId(topicName, bpmnProcessId);
        });
  }
}
