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
package io.zeebe.broker.workflow.model.transformation.handler;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableServiceTask;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.Collection;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ServiceTaskHandler implements ModelElementTransformer<ServiceTask> {

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
    serviceTask.bindLifecycleState(WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.CREATE_JOB);
    serviceTask.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_JOB_TASK);
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
      final DirectBuffer encodedHeaders = encode(taskHeaders);
      serviceTask.setEncodedHeaders(encodedHeaders);
    }
  }

  private DirectBuffer encode(ZeebeTaskHeaders taskHeaders) {
    final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    final Collection<ZeebeHeader> headers = taskHeaders.getHeaders();

    if (!headers.isEmpty()) {
      final ExpandableArrayBuffer expandableBuffer =
          new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * headers.size());
      msgPackWriter.wrap(expandableBuffer, 0);
      msgPackWriter.writeMapHeader(headers.size());

      headers.forEach(
          h -> {
            final DirectBuffer key = wrapString(h.getKey());
            msgPackWriter.writeString(key);

            final DirectBuffer value = wrapString(h.getValue());
            msgPackWriter.writeString(value);
          });

      buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());
    }

    return buffer;
  }
}
