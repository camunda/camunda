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
package io.zeebe.broker.system.workflow.repository.processor;

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.function.Consumer;

public class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final DeploymentTransformer deploymentTransformer;

  public DeploymentCreateProcessor(WorkflowRepositoryIndex index) {
    deploymentTransformer = new DeploymentTransformer(index);
  }

  @Override
  public void processRecord(
      TypedRecord<DeploymentRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {
    final DeploymentRecord deploymentEvent = event.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      final long key = streamWriter.getKeyGenerator().nextKey();
      streamWriter.writeFollowUpEvent(
          key,
          DeploymentIntent.DISTRIBUTE,
          deploymentEvent,
          m ->
              m.requestId(event.getMetadata().getRequestId())
                  .requestStreamId(event.getMetadata().getRequestStreamId()));
    } else {
      streamWriter.writeRejection(
          event,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason(),
          m ->
              m.requestId(event.getMetadata().getRequestId())
                  .requestStreamId(event.getMetadata().getRequestStreamId()));
    }
  }
}
