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
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.VariablesState.VariableListener;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
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
      long key,
      long workflowKey,
      DirectBuffer name,
      DirectBuffer value,
      long variableScopeKey,
      long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeKey(variableScopeKey)
        .setWorkflowInstanceKey(rootScopeKey)
        .setWorkflowKey(workflowKey);

    streamWriter.appendFollowUpEvent(key, VariableIntent.CREATED, record);
  }

  @Override
  public void onUpdate(
      long key,
      long workflowKey,
      DirectBuffer name,
      DirectBuffer value,
      long variableScopeKey,
      long rootScopeKey) {
    record
        .setName(name)
        .setValue(value)
        .setScopeKey(variableScopeKey)
        .setWorkflowInstanceKey(rootScopeKey)
        .setWorkflowKey(workflowKey);

    streamWriter.appendFollowUpEvent(key, VariableIntent.UPDATED, record);
  }
}
