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

import java.util.ArrayList;
import java.util.List;

/**
 * ExecutableFlowElementContainer is currently used to represent processes as well ({@link
 * io.zeebe.model.bpmn.instance.Process}), which may seem counter intuitive; at the moment, the
 * reason is that sub processes are also modelled using the same class, and sub processes need to
 * reuse the logic for both. As this diverges (i.e. processes/sub-processes), we should refactor
 * this.
 */
public class ExecutableFlowElementContainer extends ExecutableActivity {

  private final List<ExecutableCatchEventElement> startEvents;

  public ExecutableFlowElementContainer(String id) {
    super(id);
    startEvents = new ArrayList<>();
  }

  public ExecutableCatchEventElement getNoneStartEvent() {
    for (ExecutableCatchEventElement startEvent : startEvents) {
      if (startEvent.isNone()) {
        return startEvent;
      }
    }
    return null;
  }

  public List<ExecutableCatchEventElement> getStartEvents() {
    return startEvents;
  }

  public void addStartEvent(ExecutableCatchEventElement startEvent) {
    this.startEvents.add(startEvent);
  }
}
