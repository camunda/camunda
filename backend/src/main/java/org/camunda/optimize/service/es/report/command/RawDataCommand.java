package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.service.es.report.command.mapping.RawDataSingleReportResultDtoMapper;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;

public class RawDataCommand extends ReportCommand<RawDataSingleReportResultDto> {

  private static final Long RAW_DATA_LIMIT = 1_000L;

  private final RawDataSingleReportResultDtoMapper rawDataSingleReportResultDtoMapper =
      new RawDataSingleReportResultDtoMapper(RAW_DATA_LIMIT);

  public RawDataSingleReportResultDto evaluate() {
    logger.debug("Evaluating raw data report for process definition key [{}] and version [{}]",
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setQuery(query)
        .addSort(ProcessInstanceType.START_DATE, SortOrder.DESC)
        .setFetchSource(null, EVENTS)
        .setSize(RAW_DATA_LIMIT.intValue())
        .get();

    return rawDataSingleReportResultDtoMapper.mapFrom(scrollResp, objectMapper);
  }

}
