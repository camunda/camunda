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
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ServiceTaskTransformer implements ModelElementTransformer<ServiceTask> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

  @Override
  public Class<ServiceTask> getType() {
    return ServiceTask.class;
  }

  @Override
  public void transform(ServiceTask element, TransformContext context) {

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableServiceTask serviceTask =
        workflow.getElementById(element.getId(), ExecutableServiceTask.class);

    transformTaskDefinition(element, serviceTask);

    transformTaskHeaders(element, serviceTask);

    bindLifecycle(serviceTask);
  }

  private void bindLifecycle(final ExecutableServiceTask serviceTask) {
    serviceTask.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.SERVICE_TASK_ELEMENT_ACTIVATED);
    serviceTask.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.SERVICE_TASK_ELEMENT_TERMINATING);
  }

  private void transformTaskDefinition(
      ServiceTask element, final ExecutableServiceTask serviceTask) {
    final ZeebeTaskDefinition taskDefinition =
        element.getSingleExtensionElement(ZeebeTaskDefinition.class);

    serviceTask.setType(taskDefinition.getType());
    serviceTask.setRetries(taskDefinition.getRetries());
  }

  private void transformTaskHeaders(ServiceTask element, final ExecutableServiceTask serviceTask) {
    final ZeebeTaskHeaders taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);

    if (taskHeaders != null) {
      final List<ZeebeHeader> validHeaders =
          taskHeaders.getHeaders().stream()
              .filter(this::isValidHeader)
              .collect(Collectors.toList());

      if (validHeaders.size() < taskHeaders.getHeaders().size()) {
        LOG.warn(
            "Ignoring invalid headers for task '{}'. Must have non-empty key and value.",
            element.getName());
      }

      if (!validHeaders.isEmpty()) {
        final DirectBuffer encodedHeaders = encode(validHeaders);
        serviceTask.setEncodedHeaders(encodedHeaders);
      }
    }
  }

  private DirectBuffer encode(List<ZeebeHeader> taskHeaders) {
    final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    final ExpandableArrayBuffer expandableBuffer =
        new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * taskHeaders.size());

    msgPackWriter.wrap(expandableBuffer, 0);
    msgPackWriter.writeMapHeader(taskHeaders.size());

    taskHeaders.forEach(
        h -> {
          if (isValidHeader(h)) {
            final DirectBuffer key = wrapString(h.getKey());
            msgPackWriter.writeString(key);

            final DirectBuffer value = wrapString(h.getValue());
            msgPackWriter.writeString(value);
          }
        });

    buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());

    return buffer;
  }

  private boolean isValidHeader(ZeebeHeader header) {
    return header != null
        && header.getValue() != null
        && !header.getValue().isEmpty()
        && header.getKey() != null
        && !header.getKey().isEmpty();
  }
}
