package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataVariableDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;

public class RawDataCommand extends ReportCommand {

  private static final Long RAW_DATA_LIMIT = 1_000L;

  public ReportResultDto evaluate() throws IOException {
    logger.debug("Evaluating raw data report for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(reportData.getProcessDefinitionId());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessInstanceType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setFetchSource(null,  EVENTS)
      .setSize(1000)
      .get();

    List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        ProcessInstanceDto processInstanceDto =
          objectMapper.readValue(hit.getSourceAsString(), ProcessInstanceDto.class);
        RawDataProcessInstanceDto dataEntry = convertToRawDataEntry(processInstanceDto);
        rawData.add(dataEntry);
      }

      scrollResp = esclient.
        prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0 && rawData.size() < RAW_DATA_LIMIT);
    rawData = cutRawDataSizeToMaxSize(rawData);

    return createResult(reportData, rawData);
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(ProcessInstanceDto processInstanceDto) {
    RawDataProcessInstanceDto rawDataInstance = new RawDataProcessInstanceDto();
    rawDataInstance.setProcessInstanceId(processInstanceDto.getProcessInstanceId());
    rawDataInstance.setProcessDefinitionId(processInstanceDto.getProcessDefinitionId());
    rawDataInstance.setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey());
    rawDataInstance.setStartDate(convertDate(processInstanceDto.getStartDate()));
    rawDataInstance.setEndDate(convertDate(processInstanceDto.getEndDate()));
    rawDataInstance.setEngineName(processInstanceDto.getEngine());

    List<RawDataVariableDto> variables =
      processInstanceDto
      .obtainAllVariables()
      .stream()
      .map(var -> new RawDataVariableDto(var.getName(), var.getValue()))
      .collect(Collectors.toList());

    rawDataInstance.setVariables(variables);
    return rawDataInstance;
  }

  private LocalDateTime convertDate(Date date) {
    if (date != null) {
      return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    } else {
      return null;
    }
  }

  private List<RawDataProcessInstanceDto> cutRawDataSizeToMaxSize(List<RawDataProcessInstanceDto> rawData) {
    int maxSize = Math.min(RAW_DATA_LIMIT.intValue(), rawData.size());
    rawData = rawData.subList(0, maxSize);
    return rawData;
  }

  private RawDataReportResultDto createResult(ReportDataDto reportData,
                                              List<RawDataProcessInstanceDto> rawDataResult) {
    RawDataReportResultDto result = new RawDataReportResultDto();
    result.copyReportDataProperties(reportData);
    result.setResult(rawDataResult);
    return result;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = QueryBuilders.boolQuery()
      .must(QueryBuilders.termQuery("processDefinitionId", processDefinitionId));
    return query;
  }
}
