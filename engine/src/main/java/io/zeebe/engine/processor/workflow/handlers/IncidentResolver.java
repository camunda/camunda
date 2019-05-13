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
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.state.instance.IncidentState;

public class IncidentResolver {
  private final IncidentState incidentState;

  public IncidentResolver(IncidentState incidentState) {
    this.incidentState = incidentState;
  }

  public void resolveIncidents(BpmnStepContext context) {
    resolveIncidents(context, context.getRecord().getKey());
  }

  public void resolveIncidents(BpmnStepContext context, long scopeKey) {
    incidentState.forExistingWorkflowIncident(
        scopeKey, (record, key) -> context.getOutput().appendResolvedIncidentEvent(key, record));
  }
}
