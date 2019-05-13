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

import io.zeebe.model.bpmn.util.time.Timer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableCatchEventElement extends ExecutableFlowNode
    implements ExecutableCatchEvent, ExecutableCatchEventSupplier {
  private final List<ExecutableCatchEvent> events = Collections.singletonList(this);

  private ExecutableMessage message;

  private Timer timer;

  public ExecutableCatchEventElement(String id) {
    super(id);
  }

  @Override
  public boolean isMessage() {
    return message != null;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  public void setMessage(ExecutableMessage message) {
    this.message = message;
  }

  @Override
  public boolean isTimer() {
    return timer != null;
  }

  @Override
  public Timer getTimer() {
    return timer;
  }

  public void setTimer(Timer timer) {
    this.timer = timer;
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return Collections.singleton(getId());
  }
}
