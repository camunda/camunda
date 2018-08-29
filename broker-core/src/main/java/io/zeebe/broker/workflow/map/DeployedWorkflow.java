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

import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import org.agrona.DirectBuffer;

public class DeployedWorkflow {
  private final ExecutableWorkflow workflow;
  private final long key;
  private final int version;
  private final DirectBuffer resource;
  private final DirectBuffer resourceName;
  private final DirectBuffer bpmnProcessId;

  public DeployedWorkflow(
      final ExecutableWorkflow workflow,
      final long key,
      final int version,
      final DirectBuffer resource,
      final DirectBuffer resourceName,
      final DirectBuffer bpmnProcessId) {
    this.workflow = workflow;
    this.key = key;
    this.version = version;
    this.resource = resource;
    this.resourceName = resourceName;
    this.bpmnProcessId = bpmnProcessId;
  }

  public DirectBuffer getResourceName() {
    return resourceName;
  }

  public ExecutableWorkflow getWorkflow() {
    return workflow;
  }

  public int getVersion() {
    return version;
  }

  public long getKey() {
    return key;
  }

  public DirectBuffer getResource() {
    return resource;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }
}
