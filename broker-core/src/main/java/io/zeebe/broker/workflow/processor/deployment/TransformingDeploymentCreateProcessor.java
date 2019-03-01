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

import static io.zeebe.broker.workflow.state.TimerInstance.NO_ELEMENT_INSTANCE;

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.deployment.transform.DeploymentTransformer;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public class TransformingDeploymentCreateProcessor
    implements TypedRecordProcessor<DeploymentRecord> {

  public static final String DEPLOYMENT_ALREADY_EXISTS_MESSAGE =
      "Expected to create a new deployment with key '%d', but there is already an existing deployment with that key";
  private final DeploymentTransformer deploymentTransformer;
  private final WorkflowState workflowState;
  private final CatchEventBehavior catchEventBehavior;

  public TransformingDeploymentCreateProcessor(
      final ZeebeState zeebeState, CatchEventBehavior catchEventBehavior) {
    this.workflowState = zeebeState.getWorkflowState();
    this.deploymentTransformer = new DeploymentTransformer(zeebeState);
    this.catchEventBehavior = catchEventBehavior;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final DeploymentRecord deploymentEvent = command.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      final long key = streamWriter.getKeyGenerator().nextKey();
      if (workflowState.putDeployment(key, deploymentEvent)) {
        responseWriter.writeEventOnCommand(key, DeploymentIntent.CREATED, deploymentEvent, command);
        streamWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);

        createTimerIfTimerStartEvent(command, streamWriter);
      } else {
        // should not be possible
        final String reason = String.format(DEPLOYMENT_ALREADY_EXISTS_MESSAGE, key);
        responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, reason);
        streamWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, reason);
      }
    } else {
      responseWriter.writeRejectionOnCommand(
          command,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
      streamWriter.appendRejection(
          command,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
    }
  }

  private void createTimerIfTimerStartEvent(
      TypedRecord<DeploymentRecord> record, TypedStreamWriter streamWriter) {
    for (Workflow workflow : record.getValue().workflows()) {
      final List<ExecutableCatchEventElement> startEvents =
          workflowState.getWorkflowByKey(workflow.getKey()).getWorkflow().getStartEvents();

      workflowState
          .getTimerState()
          .forEachTimerForElementInstance(
              NO_ELEMENT_INSTANCE,
              timer -> {
                final DirectBuffer timerBpmnId =
                    workflowState.getWorkflowByKey(timer.getWorkflowKey()).getBpmnProcessId();

                if (timerBpmnId.equals(workflow.getBpmnProcessId())) {
                  catchEventBehavior.unsubscribeFromTimerEvent(timer, streamWriter);
                }
              });

      for (ExecutableCatchEventElement startEvent : startEvents) {
        if (startEvent.isTimer()) {
          catchEventBehavior.subscribeToTimerEvent(
              NO_ELEMENT_INSTANCE,
              NO_ELEMENT_INSTANCE,
              workflow.getKey(),
              startEvent.getId(),
              startEvent.getTimer(),
              streamWriter);
        }
      }
    }
  }
}
