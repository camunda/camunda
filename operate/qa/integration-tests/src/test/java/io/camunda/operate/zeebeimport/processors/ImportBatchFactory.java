/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class ImportBatchFactory {

  private final ObjectMapper zeebeRecordObjectMapper;
  private final ProtocolFactory zeebeProtocolFactory = new ProtocolFactory();
  private final int partitionId;

  public ImportBatchFactory(int partitionId, ObjectMapper objectMapper) {
    this.zeebeRecordObjectMapper = objectMapper.copy().registerModule(new ZeebeProtocolModule());
    this.partitionId = partitionId;
  }

  public ImportBatchBuilder newBatch(ValueType valueType, ImportValueType importValueType) {
    final ImportBatchBuilder importBatchBuilder = new ImportBatchBuilder();
    importBatchBuilder.valueType = valueType;
    importBatchBuilder.importValueType = importValueType;

    return importBatchBuilder;
  }

  public class ImportBatchBuilder {
    private ValueType valueType;
    private ImportValueType importValueType;

    private List<Record<RecordValue>> records = new ArrayList<>();

    public ImportBatchBuilder withRecord(
        UnaryOperator<ImmutableRecord.Builder<RecordValue>> modifier) {

      final Record<RecordValue> record = zeebeProtocolFactory.generateRecord(valueType, modifier);

      records.add(record);

      return this;
    }

    public ImportBatch build() {

      final List<HitEntity> jsonRecords = new ArrayList<>();

      records.forEach(
          r -> {
            final HitEntity jsonRecord = new HitEntity();

            try {
              jsonRecord.setSourceAsString(zeebeRecordObjectMapper.writeValueAsString(r));
            } catch (JsonProcessingException e) {
              throw new RuntimeException("Could not covert record to JSON", e);
            }
            // jsonRecord.setIndex is currently not implemented

            jsonRecords.add(jsonRecord);
          });

      return new ImportBatch(partitionId, importValueType, jsonRecords, null);
    }
  }
}
