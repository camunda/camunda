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

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.deployment.data.DeploymentRecord;
import io.zeebe.broker.workflow.deployment.transform.DeploymentTransformer;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.function.Consumer;

public class TransformingDeploymentCreateProcessor
    implements TypedRecordProcessor<DeploymentRecord> {

  private final DeploymentTransformer deploymentTransformer;
  private final WorkflowState workflowState;

  public TransformingDeploymentCreateProcessor(final WorkflowState workflowState) {
    this.workflowState = workflowState;
    this.deploymentTransformer = new DeploymentTransformer(workflowState);
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final DeploymentRecord deploymentEvent = event.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      final long key = streamWriter.getKeyGenerator().nextKey();
      if (workflowState.putDeployment(key, deploymentEvent)) {
        responseWriter.writeEventOnCommand(key, DeploymentIntent.CREATED, event);
        streamWriter.writeFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);
      } else {
        // should not be possible
        responseWriter.writeRejectionOnCommand(
            event, RejectionType.NOT_APPLICABLE, "Deployment already exist");
        streamWriter.writeRejection(
            event, RejectionType.NOT_APPLICABLE, "Deployment already exist");
      }
    } else {
      responseWriter.writeRejectionOnCommand(
          event,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
      streamWriter.writeRejection(
          event,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
    }
  }
}
