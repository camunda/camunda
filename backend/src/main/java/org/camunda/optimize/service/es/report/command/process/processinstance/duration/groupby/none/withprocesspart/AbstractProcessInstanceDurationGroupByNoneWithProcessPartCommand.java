package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.AbstractProcessInstanceDurationGroupByNoneCommand;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.addProcessPartQuery;
import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.createProcessPartAggregation;


public abstract class AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand
    extends AbstractProcessInstanceDurationGroupByNoneCommand {

  protected abstract long processAggregation(Aggregations aggregations);

  @Override
  public BoolQueryBuilder setupBaseQuery(ProcessReportDataDto processReportData) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processReportData);
    ProcessPartDto processPart = processReportData.getParameters().getProcessPart();
    return addProcessPartQuery(boolQueryBuilder, processPart.getStart(), processPart.getEnd());
  }



  @Override
  protected AggregationBuilder createAggregationOperation(String fieldName) {
    ProcessPartDto processPart = getReportData().getParameters().getProcessPart();
    return createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }
}
