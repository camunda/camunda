package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart;

import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.AbstractProcessInstanceDurationGroupByStartDateCommand;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;


public abstract class AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand
    extends AbstractProcessInstanceDurationGroupByStartDateCommand {

  @Override
  protected BoolQueryBuilder setupBaseQuery(String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processDefinitionKey, processDefinitionVersion);
    ProcessPartDto processPart = getProcessReportData().getParameters().getProcessPart();
    return ProcessPartQueryUtil.addProcessPartQuery(boolQueryBuilder, processPart.getStart(), processPart.getEnd());
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    ProcessPartDto processPart = getProcessReportData().getParameters().getProcessPart();
    return ProcessPartQueryUtil.createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }
}
