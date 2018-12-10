package org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart;

import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.AbstractProcessInstanceDurationByVariableCommand;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.addProcessPartQuery;
import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.createProcessPartAggregation;


public abstract class AbstractProcessInstanceDurationGroupByVariableWithProcessPartCommand
    extends AbstractProcessInstanceDurationByVariableCommand {

  @Override
  protected BoolQueryBuilder setupBaseQuery(String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processDefinitionKey, processDefinitionVersion);
    ProcessPartDto processPart = getProcessReportData().getParameters().getProcessPart();
    return addProcessPartQuery(boolQueryBuilder, processPart.getStart(), processPart.getEnd());
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    ProcessPartDto processPart = getProcessReportData().getParameters().getProcessPart();
    return createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }
}
