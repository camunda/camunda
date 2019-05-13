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
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableEventBasedGateway extends ExecutableFlowNode
    implements ExecutableCatchEventSupplier {

  private List<ExecutableCatchEvent> events;
  private List<DirectBuffer> eventIds;

  public ExecutableEventBasedGateway(String id) {
    super(id);
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }

  public void setEvents(List<ExecutableCatchEvent> events) {
    this.events = events;
    this.eventIds = new ArrayList<>(events.size());

    for (final ExecutableCatchEvent event : events) {
      eventIds.add(event.getId());
    }
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return eventIds;
  }
}
