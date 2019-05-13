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
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;

public class IncidentEventProcessors {

  public static void addProcessors(
      TypedEventStreamProcessorBuilder typedEventStreamProcessorBuilder,
      ZeebeState zeebeState,
      BpmnStepProcessor bpmnStepProcessor) {
    typedEventStreamProcessorBuilder
        .onCommand(
            ValueType.INCIDENT, IncidentIntent.CREATE, new CreateIncidentProcessor(zeebeState))
        .onCommand(
            ValueType.INCIDENT,
            IncidentIntent.RESOLVE,
            new ResolveIncidentProcessor(bpmnStepProcessor, zeebeState));
  }
}
