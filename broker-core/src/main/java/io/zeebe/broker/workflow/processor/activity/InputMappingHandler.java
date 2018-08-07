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
package io.zeebe.broker.workflow.processor.activity;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.ActivityInstanceMap;
import io.zeebe.broker.workflow.map.PayloadCache;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex;
import io.zeebe.broker.workflow.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.msgpack.mapping.MappingProcessor;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class InputMappingHandler implements BpmnStepHandler<ExecutableFlowNode> {

  private final PayloadCache payloadCache;
  private final WorkflowInstanceIndex workflowInstanceIndex;
  private final ActivityInstanceMap activityInstanceMap;

  private final MappingProcessor payloadMappingProcessor = new MappingProcessor(4096);
  private UnsafeBuffer wfInstancePayload = new UnsafeBuffer(0, 0);

  public InputMappingHandler(
      PayloadCache payloadCache,
      WorkflowInstanceIndex workflowInstanceIndex,
      ActivityInstanceMap activityInstanceMap) {
    this.payloadCache = payloadCache;
    this.workflowInstanceIndex = workflowInstanceIndex;
    this.activityInstanceMap = activityInstanceMap;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowNode> context) {

    final TypedRecord<WorkflowInstanceRecord> record = context.getRecord();
    final WorkflowInstanceRecord activityEvent = context.getValue();
    wfInstancePayload.wrap(activityEvent.getPayload());

    final Mapping[] inputMappings = context.getElement().getInputMappings();

    MappingException mappingException = null;

    // only if we have no default mapping we have to use the mapping processor
    if (inputMappings.length > 0) {
      try {
        final int resultLen =
            payloadMappingProcessor.extract(activityEvent.getPayload(), inputMappings);
        final MutableDirectBuffer mappedPayload = payloadMappingProcessor.getResultBuffer();
        activityEvent.setPayload(mappedPayload, 0, resultLen);
      } catch (MappingException e) {
        mappingException = e;
      }
    }

    if (mappingException == null) {
      context
          .getStreamWriter()
          .writeFollowUpEvent(
              record.getKey(), WorkflowInstanceIntent.ACTIVITY_ACTIVATED, activityEvent);

      payloadCache.addPayload(
          activityEvent.getWorkflowInstanceKey(), record.getPosition(), wfInstancePayload);
    } else {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, mappingException.getMessage());
    }

    workflowInstanceIndex
        .get(activityEvent.getWorkflowInstanceKey())
        .setActivityInstanceKey(record.getKey())
        .write();

    activityInstanceMap
        .newActivityInstance(record.getKey())
        .setActivityId(activityEvent.getActivityId())
        .setJobKey(-1L)
        .write();
  }
}
