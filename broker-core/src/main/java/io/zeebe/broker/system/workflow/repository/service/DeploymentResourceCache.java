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

import io.zeebe.broker.system.workflow.repository.data.DeployedWorkflow;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex.WorkflowMetadata;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

/** Cached deployment resource buffers */
public class DeploymentResourceCache {
  private Long2ObjectHashMap<DirectBuffer> resourceCache = new Long2ObjectHashMap<>();

  private final BufferedLogStreamReader reader;

  private final DeploymentRecord deploymentEvent = new DeploymentRecord();

  public DeploymentResourceCache(final BufferedLogStreamReader reader) {
    this.reader = reader;
  }

  public DirectBuffer getResource(WorkflowMetadata workflow) {
    return resourceCache.computeIfAbsent(
        workflow.getKey(),
        (key) -> {
          if (reader.seek(workflow.getEventPosition())) {
            final LoggedEvent event = reader.next();

            event.readValue(deploymentEvent);

            final Iterator<DeployedWorkflow> deployedWorkflowsIterator =
                deploymentEvent.deployedWorkflows().iterator();

            while (deployedWorkflowsIterator.hasNext()) {
              final DeployedWorkflow deployedWorkflow = deployedWorkflowsIterator.next();

              if (deployedWorkflow.getKey() == key) {
                return BufferUtil.cloneBuffer(
                    deploymentEvent.resources().iterator().next().getResource());
              }
            }
          }

          return null;
        });
  }
}
