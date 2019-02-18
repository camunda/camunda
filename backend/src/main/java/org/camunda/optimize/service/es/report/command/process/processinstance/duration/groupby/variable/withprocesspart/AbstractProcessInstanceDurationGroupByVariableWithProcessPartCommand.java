package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.AbstractProcessInstanceDurationByVariableCommand;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;


public abstract class AbstractProcessInstanceDurationGroupByVariableWithProcessPartCommand
    extends AbstractProcessInstanceDurationByVariableCommand {

  @Override
  public BoolQueryBuilder setupBaseQuery(ProcessReportDataDto processReportData) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processReportData);
    ProcessPartDto processPart = processReportData.getParameters().getProcessPart();
    return ProcessPartQueryUtil.addProcessPartQuery(boolQueryBuilder, processPart.getStart(), processPart.getEnd());
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    ProcessPartDto processPart = getReportData().getParameters().getProcessPart();
    return ProcessPartQueryUtil.createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }
}
