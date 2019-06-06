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
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class MessageCorrelationKeyContext {
  private long variablesScopeKey;
  private DirectBuffer variablesDocument;
  private VariablesDocumentSupplier variablesSupplier;

  public MessageCorrelationKeyContext() {}

  public MessageCorrelationKeyContext(
      VariablesDocumentSupplier variablesSupplier, long variablesScopeKey) {
    this.variablesSupplier = variablesSupplier;
    this.variablesScopeKey = variablesScopeKey;
  }

  public long getVariablesScopeKey() {
    return variablesScopeKey;
  }

  public MessageCorrelationKeyContext reset() {
    variablesSupplier = null;
    variablesScopeKey = -1;
    variablesDocument = null;

    return this;
  }

  public MessageCorrelationKeyContext setVariablesSupplier(
      VariablesDocumentSupplier variablesSupplier) {
    this.variablesSupplier = variablesSupplier;
    return this;
  }

  public MessageCorrelationKeyContext setVariablesScopeKey(long variablesScopeKey) {
    this.variablesScopeKey = variablesScopeKey;
    return this;
  }

  public DirectBuffer getVariablesAsDocument() {
    assert variablesScopeKey >= 0 : "no variables scope key given";
    assert variablesSupplier != null : "no variables supplier given";

    if (variablesDocument == null) {
      final DirectBuffer document = variablesSupplier.getVariablesAsDocument(variablesScopeKey);

      if (document != null) { // must be cloned in case the supplier reuses the given buffer
        variablesDocument = BufferUtil.cloneBuffer(document);
      }
    }

    return variablesDocument;
  }

  @FunctionalInterface
  public interface VariablesDocumentSupplier {
    DirectBuffer getVariablesAsDocument(long scopeKey);
  }
}
