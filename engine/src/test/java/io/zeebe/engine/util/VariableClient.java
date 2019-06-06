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
package io.zeebe.engine.util;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableDocumentRecordValue;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

public class VariableClient {

  private final VariableDocumentRecord variableDocumentRecord;
  private final StreamProcessorRule environmentRule;

  public VariableClient(StreamProcessorRule environmentRule, long scopeKey) {
    this.environmentRule = environmentRule;
    variableDocumentRecord = new VariableDocumentRecord();
    variableDocumentRecord.setScopeKey(scopeKey);
  }

  public VariableClient withDocument(Map<String, Object> variables) {
    variableDocumentRecord.setDocument(
        new UnsafeBuffer(MsgPackUtil.asMsgPack(variables).byteArray()));
    return this;
  }

  public VariableClient withUpdateSemantic(VariableDocumentUpdateSemantic semantic) {
    variableDocumentRecord.setUpdateSemantics(semantic);
    return this;
  }

  public Record<VariableDocumentRecordValue> update() {
    final long position =
        environmentRule.writeCommand(VariableDocumentIntent.UPDATE, variableDocumentRecord);

    return RecordingExporter.variableDocumentRecords()
        .withIntent(VariableDocumentIntent.UPDATED)
        .withSourceRecordPosition(position)
        .getFirst();
  }
}
