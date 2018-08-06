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

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.ExecutableIntermediateMessageCatchEvent;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;

public class IntermediateCatchEventHandler
    implements ModelElementTransformer<IntermediateCatchEvent> {

  @Override
  public Class<IntermediateCatchEvent> getType() {
    return IntermediateCatchEvent.class;
  }

  @Override
  public void transform(IntermediateCatchEvent element, TransformContext context) {

    // only message supported at this point

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableIntermediateMessageCatchEvent executableElement =
        workflow.getElementById(element.getId(), ExecutableIntermediateMessageCatchEvent.class);

    final MessageEventDefinition eventDefinition =
        (MessageEventDefinition) element.getEventDefinitions().iterator().next();
    final Message message = eventDefinition.getMessage();

    final ZeebeSubscription subscription =
        message
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(ZeebeSubscription.class)
            .singleResult();

    final JsonPathQueryCompiler queryCompiler = context.getJsonPathQueryCompiler();
    final JsonPathQuery query = queryCompiler.compile(subscription.getCorrelationKey());

    executableElement.setCorrelationKey(query);
    executableElement.setMessageName(BufferUtil.wrapString(message.getName()));

    bindLifecycle(context, executableElement);
  }

  private void bindLifecycle(
      TransformContext context, final ExecutableIntermediateMessageCatchEvent executableElement) {
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.CATCH_EVENT_OCCURRED, context.getCurrentFlowNodeOutgoingStep());
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ACTIVITY_TERMINATING, BpmnStep.TERMINATE_ELEMENT);
  }
}
