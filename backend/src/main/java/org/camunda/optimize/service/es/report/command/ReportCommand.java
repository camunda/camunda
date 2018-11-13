package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ReportCommand <T extends SingleReportResultDto>  implements Command {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected SingleReportDataDto reportData;
  protected Client esclient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;
  protected QueryFilterEnhancer queryFilterEnhancer;

  @Override
  public T evaluate(CommandContext commandContext) throws OptimizeException {
    reportData = commandContext.getReportData();
    esclient = commandContext.getEsclient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    queryFilterEnhancer = commandContext.getQueryFilterEnhancer();
    T evaluationResult = evaluate();
    return filterResultDataBasedOnPD(evaluationResult);
  }

  protected T filterResultDataBasedOnPD(T evaluationResult) {
    return evaluationResult;
  }

  protected abstract T evaluate() throws OptimizeException;

  protected BoolQueryBuilder setupBaseQuery(String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionKey", processDefinitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
      query = query
        .must(termQuery("processDefinitionVersion", processDefinitionVersion));
    }
    return query;
  }

  protected ProcessDefinitionOptimizeDto fetchLatestDefinitionXml() {
    ProcessDefinitionOptimizeDto result = null;
    BoolQueryBuilder query = boolQuery()
        .must(termQuery("processDefinitionKey", reportData.getProcessDefinitionKey()));

    SearchResponse scrollResp = esclient
        .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .addSort(ProcessInstanceType.PROCESS_DEFINITION_VERSION, SortOrder.DESC)
        .setSize(1)
        .get();

    if (scrollResp.getHits().getTotalHits() > 0) {
      try {
        ProcessInstanceDto processInstanceDto =
            objectMapper.readValue(scrollResp.getHits().getAt(0).getSourceAsString(), ProcessInstanceDto.class);
        String processDefinitionId = processInstanceDto.getProcessDefinitionId();

        GetResponse response = esclient.prepareGet(
            getOptimizeIndexAliasForType(configurationService.getProcessDefinitionType()),
            configurationService.getProcessDefinitionType(),
            processDefinitionId)
            .get();

        result = new ProcessDefinitionOptimizeDto ();
        result.setBpmn20Xml(response.getSource().get(PROCESS_DEFINITION_XML).toString());
        result.setId(response.getSource().get(PROCESS_DEFINITION_ID).toString());
        if (response.getSource().get(ENGINE) != null) {
          result.setEngine(response.getSource().get(ENGINE).toString());
        }
        result.setFlowNodeNames((Map<String, String>) response.getSource().get(FLOW_NODE_NAMES));

      } catch (IOException e) {
        logger.error("can't parse process instance", e);
      }
    }
    return result;
  }
}
