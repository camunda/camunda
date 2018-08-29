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
package io.zeebe.broker.workflow.processor.deployment;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.deployment.data.DeploymentRecord;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.DeploymentIntent;

public class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final WorkflowCache workflowCache;

  public DeploymentCreateProcessor(final WorkflowCache cache) {
    workflowCache = cache;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final DeploymentRecord deploymentEvent = event.getValue();
    if (workflowCache.addWorkflow(event.getKey(), deploymentEvent)) {
      streamWriter.writeFollowUpEvent(event.getKey(), DeploymentIntent.CREATED, deploymentEvent);
    } else {
      streamWriter.writeRejection(event, RejectionType.NOT_APPLICABLE, "Deployment already exist");
    }
  }
}
