package org.camunda.optimize.service.es.reader;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class BranchAnalysisReader {

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private DateFilterHelper dateFilterHelper;

  public BranchAnalysisDto branchAnalysis(BranchAnalysisQueryDto request) {
    ValidationHelper.validate(request);
    BranchAnalysisDto result = new BranchAnalysisDto();
    List<String> gatewayOutcomes = fetchGatewayOutcomes(request.getProcessDefinitionId(), request.getGateway());

    for (String activity : gatewayOutcomes) {
      BranchAnalysisOutcomeDto branchAnalysis = branchAnalysis(activity, request);
      result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
    }

    result.setEndEvent(request.getEnd());
    result.setTotal(calculateActivityCount(request.getEnd(), request));

    return result;
  }

  private BranchAnalysisOutcomeDto branchAnalysis(String activityId, BranchAnalysisQueryDto request) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(activityId);
    result.setActivityCount(calculateActivityCount(activityId, request));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(activityId, request));

    return result;
  }

  private long calculateReachedEndEventActivityCount(String activityId, BranchAnalysisQueryDto request) {
    BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(QueryBuilders.matchQuery("processDefinitionId", request.getProcessDefinitionId()))
      .must(QueryBuilders.termQuery("activityList", request.getGateway()))
      .must(QueryBuilders.termQuery("activityList", activityId))
      .must(QueryBuilders.termQuery("activityList", request.getEnd()));

    return executeQuery(request, query);
  }

  private long calculateActivityCount(String activityId, BranchAnalysisQueryDto request) {
    BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(QueryBuilders.matchQuery("processDefinitionId", request.getProcessDefinitionId()))
        .must(QueryBuilders.termQuery("activityList", request.getGateway()))
        .must(QueryBuilders.termQuery("activityList", activityId ));

    return executeQuery(request, query);
  }

  private long executeQuery(BranchAnalysisQueryDto request, BoolQueryBuilder query) {
    if (request.getFilter() != null) {
      query = dateFilterHelper.addFilters(query, request.getFilter());
    }

    SearchResponse sr = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getBranchAnalysisDataType())
        .setQuery(query)
        .setSize(0)
        .get();

    return sr.getHits().totalHits();
  }

  private List<String> fetchGatewayOutcomes(String processDefinitionId, String gatewayActivityId) {
    List<String> result = new ArrayList<>();
    String xml = processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
    BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget().getId());
    }
    return result;
  }
}
