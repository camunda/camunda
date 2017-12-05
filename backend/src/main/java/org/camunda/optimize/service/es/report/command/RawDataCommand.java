package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;

public class RawDataCommand extends ReportCommand {

  private static final Long RAW_DATA_LIMIT = 1_000L;

  public ReportResultDto evaluate() throws IOException {
    logger.debug("Evaluating raw data report for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(reportData.getProcessDefinitionId());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .addSort(ProcessInstanceType.START_DATE, SortOrder.DESC)
      .setFetchSource(null,  EVENTS)
      .setSize(1000)
      .get();

    List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    Set<String> allVariableNames = new HashSet<>();
    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        ProcessInstanceDto processInstanceDto =
          objectMapper.readValue(hit.getSourceAsString(), ProcessInstanceDto.class);
        Map<String, Object> variables = getVariables(processInstanceDto);
        allVariableNames.addAll(variables.keySet());
        RawDataProcessInstanceDto dataEntry = convertToRawDataEntry(processInstanceDto, variables);
        rawData.add(dataEntry);
      }

      scrollResp = esclient.
        prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0 && rawData.size() < RAW_DATA_LIMIT);
    ensureEveryRawDataInstanceContainsAllVariableNames(rawData, allVariableNames);
    rawData = cutRawDataSizeToMaxSize(rawData);

    return createResult(rawData);
  }

  private void ensureEveryRawDataInstanceContainsAllVariableNames(List<RawDataProcessInstanceDto> rawData,
                                                                  Set<String> allVariableNames ) {
    rawData
      .forEach(data -> allVariableNames
        .forEach(varName -> data.getVariables().putIfAbsent(varName, ""))
      );
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(ProcessInstanceDto processInstanceDto,
                                                          Map<String, Object> variables) {
    RawDataProcessInstanceDto rawDataInstance = new RawDataProcessInstanceDto();
    rawDataInstance.setProcessInstanceId(processInstanceDto.getProcessInstanceId());
    rawDataInstance.setProcessDefinitionId(processInstanceDto.getProcessDefinitionId());
    rawDataInstance.setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey());
    rawDataInstance.setStartDate(convertDate(processInstanceDto.getStartDate()));
    rawDataInstance.setEndDate(convertDate(processInstanceDto.getEndDate()));
    rawDataInstance.setEngineName(processInstanceDto.getEngine());

    rawDataInstance.setVariables(variables);
    return rawDataInstance;
  }

  private Map<String, Object> getVariables(ProcessInstanceDto processInstanceDto) {
    return processInstanceDto
    .obtainAllVariables()
    .stream()
    .collect(Collectors.toMap(VariableInstanceDto::getName, VariableInstanceDto::getValue, (a,b) -> a, TreeMap::new));
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

  private RawDataReportResultDto createResult(List<RawDataProcessInstanceDto> rawDataResult) {
    RawDataReportResultDto result = new RawDataReportResultDto();
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
