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
package io.zeebe.engine.processor.workflow.variable;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.spec.MsgpackReaderException;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import org.agrona.DirectBuffer;

public class UpdateVariableDocumentProcessor implements CommandProcessor<VariableDocumentRecord> {
  private final ElementInstanceState elementInstanceState;
  private final VariablesState variablesState;

  public UpdateVariableDocumentProcessor(
      ElementInstanceState elementInstanceState, VariablesState variablesState) {
    this.elementInstanceState = elementInstanceState;
    this.variablesState = variablesState;
  }

  @Override
  public void onCommand(
      TypedRecord<VariableDocumentRecord> command,
      CommandControl<VariableDocumentRecord> controller) {
    final VariableDocumentRecord record = command.getValue();

    final ElementInstance scope = elementInstanceState.getInstance(record.getScopeKey());
    if (scope == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(
              "Expected to update variables for element with key '%d', but no such element was found",
              record.getScopeKey()));

      return;
    }

    final long workflowKey = scope.getValue().getWorkflowKey();

    if (mergeDocument(record, workflowKey, controller)) {
      controller.accept(VariableDocumentIntent.UPDATED, record);
    }
  }

  private boolean mergeDocument(
      VariableDocumentRecord record,
      long workflowKey,
      CommandControl<VariableDocumentRecord> controller) {
    try {
      getUpdateOperation(record.getUpdateSemantics())
          .apply(record.getScopeKey(), workflowKey, record.getDocument());
      return true;
    } catch (MsgpackReaderException e) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          "Expected to merge variable document for scope '{}', but its document could not be read",
          record.getScopeKey(),
          e);

      controller.reject(
          RejectionType.INVALID_ARGUMENT,
          String.format(
              "Expected document to be valid msgpack, but it could not be read: '%s'",
              e.getMessage()));
      return false;
    }
  }

  private UpdateOperation getUpdateOperation(VariableDocumentUpdateSemantic updateSemantics) {
    switch (updateSemantics) {
      case LOCAL:
        return variablesState::setVariablesLocalFromDocument;
      case PROPAGATE:
      default:
        return variablesState::setVariablesFromDocument;
    }
  }

  @FunctionalInterface
  private interface UpdateOperation {
    void apply(long scopeKey, long workflowKey, DirectBuffer document);
  }
}
