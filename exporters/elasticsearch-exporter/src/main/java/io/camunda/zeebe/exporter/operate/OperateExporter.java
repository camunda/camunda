package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.operate.handlers.EventFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.FlowNodeInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromActivityInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.SequenceFlowHandler;
import io.camunda.zeebe.protocol.record.Record;

public class OperateExporter {

  private ExportBatchWriter writer;

  public OperateExporter() {
    this.writer = createBatchWriter();
  }

  private ExportBatchWriter createBatchWriter() {

    return new ExportBatchWriter.Builder()
        // ImportBulkProcessor#processProcessInstanceRecords
        //   FlowNodeInstanceZeebeRecordProcessor#processProcessInstanceRecord
        .withHandler(new FlowNodeInstanceHandler())
        //   eventZeebeRecordProcessor.processProcessInstanceRecords
        .withHandler(new EventFromProcessInstanceHandler())
        //   sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord
        .withHandler(new SequenceFlowHandler())
        //   listViewZeebeRecordProcessor.processProcessInstanceRecord
        .withHandler(new ListViewFromProcessInstanceHandler())
        .withHandler(new ListViewFromActivityInstanceHandler())
        // TODO: need to choose between upsert and insert (see optimization in
        // FlowNodeInstanceZeebeRecordProcessor#canOptimizeFlowNodeInstanceIndexing)
        .build();
  }

  public void exportRecord(final Record<?> record) {

    writer.addRecord(record);

    // TODO: where to put flush?
  }
}
