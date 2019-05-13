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
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;

/** Executable* prefix in order to avoid confusion with model API classes. */
public class ExecutableWorkflow extends ExecutableFlowElementContainer {

  private final Map<DirectBuffer, AbstractFlowElement> flowElements = new HashMap<>();

  public ExecutableWorkflow(String id) {
    super(id);
    addFlowElement(this);
  }

  public void addFlowElement(AbstractFlowElement element) {
    flowElements.put(element.getId(), element);
  }

  public AbstractFlowElement getElementById(DirectBuffer id) {
    return flowElements.get(id);
  }

  /** convenience function for transformation */
  public <T extends ExecutableFlowElement> T getElementById(String id, Class<T> expectedType) {
    final DirectBuffer buffer = BufferUtil.wrapString(id);
    final ExecutableFlowElement element = flowElements.get(buffer);
    if (element == null) {
      return null;
    }

    if (expectedType.isAssignableFrom(element.getClass())) {
      return (T) element;
    } else {
      throw new RuntimeException(
          String.format(
              "Expected element with id '%s' to be instance of class '%s', but it is an instance of '%s'",
              id, expectedType.getSimpleName(), element.getClass().getSimpleName()));
    }
  }
}
