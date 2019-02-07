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
package io.zeebe.broker.workflow.processor;

import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.VariableRecord;
import io.zeebe.broker.workflow.state.VariablesState.VariableListener;
import io.zeebe.protocol.intent.VariableIntent;
import org.agrona.DirectBuffer;

public class UpdateVariableStreamWriter implements VariableListener {

  private final VariableRecord record = new VariableRecord();

  private final TypedStreamWriter streamWriter;

  public UpdateVariableStreamWriter(TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  @Override
  public void onCreate(
      DirectBuffer name, DirectBuffer value, long scopeInstanceKey, long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeInstanceKey(scopeInstanceKey)
        .setWorkflowInstanceKey(rootScopeKey);

    streamWriter.appendNewEvent(VariableIntent.CREATED, record);
  }

  @Override
  public void onUpdate(
      DirectBuffer name, DirectBuffer value, long scopeInstanceKey, long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeInstanceKey(scopeInstanceKey)
        .setWorkflowInstanceKey(rootScopeKey);

    streamWriter.appendNewEvent(VariableIntent.UPDATED, record);
  }
}
