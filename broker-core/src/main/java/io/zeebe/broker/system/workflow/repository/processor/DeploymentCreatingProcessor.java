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

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.processor.state.DeploymentsStateController;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class DeploymentCreatingProcessor implements TypedRecordProcessor<DeploymentRecord> {

  public static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;
  private final DeploymentsStateController deploymentsStateController;
  private int partitionId;

  public DeploymentCreatingProcessor(DeploymentsStateController stateController) {
    this.deploymentsStateController = stateController;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    partitionId = streamProcessor.getEnvironment().getStream().getPartitionId();
  }

  @Override
  public void processRecord(
      TypedRecord<DeploymentRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {
    final DeploymentRecord deploymentEvent = event.getValue();

    final long deploymentKey = event.getKey();
    if (!deploymentsStateController.existDeployment(deploymentKey)) {
      LOG.debug("Deployment created on partition {}", partitionId);
      deploymentsStateController.putDeploymentExistence(deploymentKey);
      streamWriter.writeFollowUpEvent(deploymentKey, DeploymentIntent.CREATED, deploymentEvent);
    } else {
      LOG.debug(
          "Deployment was already created on partition {}, reject creating command.", partitionId);
      streamWriter.writeRejection(
          event, RejectionType.NOT_APPLICABLE, "Deployment was already created.");
    }
  }
}
