package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
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

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.BPMN_20_XML;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESSS_DEFINITION_ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ReportCommand <T extends ReportResultDto>  implements Command {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ReportDataDto reportData;
  protected Client esclient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;
  protected QueryFilterEnhancer queryFilterEnhancer;

  @Override
  public T evaluate(CommandContext commandContext) throws IOException, OptimizeException {
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

  protected abstract T evaluate() throws IOException, OptimizeException;

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

  protected ProcessDefinitionXmlOptimizeDto fetchLatestDefinitionXml() {
    ProcessDefinitionXmlOptimizeDto result = null;
    BoolQueryBuilder query = boolQuery()
        .must(termQuery("processDefinitionKey", reportData.getProcessDefinitionKey()));

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
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
            configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()),
            configurationService.getProcessDefinitionXmlType(),
            processDefinitionId)
            .get();

        result = new ProcessDefinitionXmlOptimizeDto ();
        result.setBpmn20Xml(response.getSource().get(BPMN_20_XML).toString());
        result.setProcessDefinitionId(response.getSource().get(PROCESSS_DEFINITION_ID).toString());
        if (response.getSource().get(ENGINE) != null) {
          result.setEngine(response.getSource().get(ENGINE).toString());
        }
        result.setFlowNodeNames((Map<String, String>) response.getSource().get(ProcessDefinitionXmlType.FLOW_NODE_NAMES));

      } catch (IOException e) {
        logger.error("can't parse process instance", e);
      }
    }
    return result;
  }
}
