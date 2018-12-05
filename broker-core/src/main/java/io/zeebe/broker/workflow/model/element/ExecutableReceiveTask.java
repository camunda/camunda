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
package io.zeebe.broker.workflow.model.element;

import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import java.util.Collections;
import java.util.List;

public class ExecutableReceiveTask extends ExecutableActivity
    implements ExecutableCatchEvent, ExecutableCatchEventSupplier {

  private final List<ExecutableCatchEvent> events = Collections.singletonList(this);

  public ExecutableReceiveTask(String id) {
    super(id);
  }

  private ExecutableMessage message;

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  public void setMessage(ExecutableMessage message) {
    this.message = message;
  }

  @Override
  public boolean isTimer() {
    return false;
  }

  @Override
  public boolean isMessage() {
    return true;
  }

  @Override
  public RepeatingInterval getTimer() {
    return null;
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }
}
