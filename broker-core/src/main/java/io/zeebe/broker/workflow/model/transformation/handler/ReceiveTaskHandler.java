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

import io.zeebe.broker.workflow.model.ExecutableMessageCatchElement;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;

public class ReceiveTaskHandler implements ModelElementTransformer<ReceiveTask> {

  private final MessageCatchElementHandler messageCatchHandler = new MessageCatchElementHandler();

  @Override
  public Class<ReceiveTask> getType() {
    return ReceiveTask.class;
  }

  @Override
  public void transform(ReceiveTask element, TransformContext context) {

    // only message supported at this point

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableMessageCatchElement executableElement =
        workflow.getElementById(element.getId(), ExecutableMessageCatchElement.class);

    final Message message = element.getMessage();

    messageCatchHandler.transform(executableElement, message, context);
  }
}
