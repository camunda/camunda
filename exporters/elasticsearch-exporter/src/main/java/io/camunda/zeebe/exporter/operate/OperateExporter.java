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

  

  public void exportRecord(final Record<?> record) {

    writer.addRecord(record);

    // TODO: where to put flush?
  }
}
