/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.deployment;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import org.agrona.DirectBuffer;

public class DeployedWorkflow {
  private final ExecutableWorkflow workflow;
  private final PersistedWorkflow persistedWorkflow;

  public DeployedWorkflow(final ExecutableWorkflow workflow, PersistedWorkflow persistedWorkflow) {
    this.workflow = workflow;
    this.persistedWorkflow = persistedWorkflow;
  }

  public DirectBuffer getResourceName() {
    return persistedWorkflow.getResourceName();
  }

  public ExecutableWorkflow getWorkflow() {
    return workflow;
  }

  public int getVersion() {
    return persistedWorkflow.getVersion();
  }

  public long getKey() {
    return persistedWorkflow.getKey();
  }

  public DirectBuffer getResource() {
    return persistedWorkflow.getResource();
  }

  public DirectBuffer getBpmnProcessId() {
    return persistedWorkflow.getBpmnProcessId();
  }

  @Override
  public String toString() {
    return "DeployedWorkflow{"
        + "workflow="
        + workflow
        + ", persistedWorkflow="
        + persistedWorkflow
        + '}';
  }
}
