package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.operate.handlers.DecisionDefinitionHandler;
import io.camunda.zeebe.exporter.operate.handlers.DecisionInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.DecisionRequirementsHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromJobHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromMessageSubscriptionHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.FlowNodeInstanceFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.FlowNodeInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.IncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromActivityInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromJobHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromVariableHandler;
import io.camunda.zeebe.exporter.operate.handlers.PostImporterQueueHandler;
import io.camunda.zeebe.exporter.operate.handlers.ProcessHandler;
import io.camunda.zeebe.exporter.operate.handlers.SequenceFlowHandler;
import io.camunda.zeebe.exporter.operate.handlers.VariableHandler;
import io.camunda.zeebe.protocol.record.Record;

public class OperateExporter {

  private ExportBatchWriter writer;

  public OperateExporter() {
    this.writer = createBatchWriter();
  }

  private ExportBatchWriter createBatchWriter() {

    return new ExportBatchWriter.Builder()
        // ImportBulkProcessor
        // #processDecisionRecords
        .withHandler(new DecisionDefinitionHandler())
        // #processDecisionRequirementsRecord
        .withHandler(new DecisionRequirementsHandler())
        // #processDecisionEvaluationRecords
        .withHandler(new DecisionInstanceHandler()) // TODO: needs concept to produce multiple entities from one record
        // #processProcessInstanceRecords
        //   FlowNodeInstanceZeebeRecordProcessor#processProcessInstanceRecord
        .withHandler(new FlowNodeInstanceHandler())
        //   eventZeebeRecordProcessor.processProcessInstanceRecords
        .withHandler(new EventFromProcessInstanceHandler())
        //   sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord
        .withHandler(new SequenceFlowHandler())
        //   listViewZeebeRecordProcessor.processProcessInstanceRecord
        .withHandler(new ListViewFromProcessInstanceHandler())
        // TODO: need to choose between upsert and insert (see optimization in
        // FlowNodeInstanceZeebeRecordProcessor#canOptimizeFlowNodeInstanceIndexing)
        .withHandler(new ListViewFromActivityInstanceHandler())
        // #processIncidentRecords
        //   incidentZeebeRecordProcessor.processIncidentRecord
        .withHandler(new IncidentHandler())
        .withHandler(new PostImporterQueueHandler())
        //   listViewZeebeRecordProcessor.processIncidentRecord
        .withHandler(new ListViewFromIncidentHandler())
        //   flowNodeInstanceZeebeRecordProcessor.processIncidentRecord
        .withHandler(new FlowNodeInstanceFromIncidentHandler())
        //   eventZeebeRecordProcessor.processIncidentRecords
        .withHandler(new EventFromIncidentHandler())
        // #processVariableRecords
        //   listViewZeebeRecordProcessor.processVariableRecords
        .withHandler(new ListViewFromVariableHandler())
        //   variableZeebeRecordProcessor.processVariableRecords
        .withHandler(new VariableHandler())
        // #processVariableDocumentRecords
        //   operationZeebeRecordProcessor.processVariableDocumentRecords
        //   TODO: currently not implemented; is needed to complete operations
        // #processProcessRecords
        //   processZeebeRecordProcessor.processDeploymentRecord
        .withHandler(new ProcessHandler())
        // #processJobRecords
        //   listViewZeebeRecordProcessor.processJobRecords
        .withHandler(new ListViewFromJobHandler())
        //   eventZeebeRecordProcessor.processJobRecords
        .withHandler(new EventFromJobHandler())
        // #processProcessMessageSubscription
        //   eventZeebeRecordProcessor.processProcessMessageSubscription
        .withHandler(new EventFromMessageSubscriptionHandler())
        .build();
  }

  public void exportRecord(final Record<?> record) {

    writer.addRecord(record);

    // TODO: where to put flush?
  }
}
