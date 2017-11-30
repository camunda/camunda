package org.camunda.optimize.service.es.reader;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisQueryDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * @author Askar Akhmerov
 */
@Component
public class BranchAnalysisReader {

  private final Logger logger = LoggerFactory.getLogger(BranchAnalysisReader.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private QueryFilterEnhancer queryFilterEnhancer;

  public BranchAnalysisDto branchAnalysis(BranchAnalysisQueryDto request) {
    ValidationHelper.validate(request);
    logger.debug("Performing branch analysis on process definition: {}", request.getProcessDefinitionId());
    
    BranchAnalysisDto result = new BranchAnalysisDto();
    BpmnModelInstance bpmnModelInstance = getBpmnModelInstance(request.getProcessDefinitionId());
    List<FlowNode> gatewayOutcomes = fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
    Set<String> activityIdsWithMultipleIncomingSequenceFlows =
      extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);

    for (FlowNode activity : gatewayOutcomes) {
      Set<String> activitiesToExcludeFromBranchAnalysis =
        extractActivitiesToExclude(gatewayOutcomes, activityIdsWithMultipleIncomingSequenceFlows, activity.getId(), request.getEnd());
      BranchAnalysisOutcomeDto branchAnalysis = branchAnalysis(activity, request, activitiesToExcludeFromBranchAnalysis);
      result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
    }

    result.setEndEvent(request.getEnd());
    result.setTotal(calculateActivityCount(request.getEnd(), request, Collections.emptySet()));

    return result;
  }

  private Set<String> extractActivitiesToExclude(List<FlowNode> gatewayOutcomes,
                                                 Set<String> activityIdsWithMultipleIncomingSequenceFlows,
                                                 String currentActivityId,
                                                 String endEventActivityId) {
    Set<String> activitiesToExcludeFromBranchAnalysis = new HashSet<>();
    for (FlowNode gatewayOutgoingNode : gatewayOutcomes) {
      String activityId = gatewayOutgoingNode.getId();
      if (!activityIdsWithMultipleIncomingSequenceFlows.contains(activityId)) {
        activitiesToExcludeFromBranchAnalysis.add(gatewayOutgoingNode.getId());
      }
    }
    activitiesToExcludeFromBranchAnalysis.remove(currentActivityId);
    activitiesToExcludeFromBranchAnalysis.remove(endEventActivityId);
    return activitiesToExcludeFromBranchAnalysis;
  }

  private BranchAnalysisOutcomeDto branchAnalysis(FlowNode flowNode, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(calculateActivityCount(flowNode.getId(), request, activitiesToExclude));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(flowNode.getId(), request, activitiesToExclude));

    return result;
  }

  private long calculateReachedEndEventActivityCount(String activityId, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery("processDefinitionId", request.getProcessDefinitionId()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd())
      );
    excludeActivities(activitiesToExclude, query);

    return executeQuery(request, query);
  }

  private long calculateActivityCount(String activityId, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery("processDefinitionId", request.getProcessDefinitionId()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));
    excludeActivities(activitiesToExclude, query);

    return executeQuery(request, query);
  }

  private void excludeActivities(Set<String> activitiesToExclude, BoolQueryBuilder query) {
    for (String excludeActivityId : activitiesToExclude) {
      query
        .mustNot(createMustMatchActivityIdQuery(excludeActivityId));
    }
  }

  private NestedQueryBuilder createMustMatchActivityIdQuery(String activityId) {
    return nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery("events.activityId", activityId),
      ScoreMode.None
    );
  }

  private long executeQuery(BranchAnalysisQueryDto request, BoolQueryBuilder query) {
    queryFilterEnhancer.addFilterToQuery(query, request.getFilter());

    SearchResponse sr = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setQuery(query)
        .setFetchSource(false)
        .setSize(0)
        .get();

    return sr.getHits().getTotalHits();
  }

  private List<FlowNode> fetchGatewayOutcomes(BpmnModelInstance bpmnModelInstance, String gatewayActivityId) {
    List<FlowNode> result = new ArrayList<>();
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget());
    }
    return result;
  }

  private BpmnModelInstance getBpmnModelInstance(String processDefinitionId) {
    String xml = processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
  }

  private Set<String> extractFlowNodesWithMultipleIncomingSequenceFlows(BpmnModelInstance bpmnModelInstance) {
    Collection<SequenceFlow> sequenceFlowCollection = bpmnModelInstance.getModelElementsByType(SequenceFlow.class);
    Set<String> activitiesWithOneIncomingSequenceFlow = new HashSet<>();
    Set<String> activityIdsWithMultipleIncomingSequenceFlows = new HashSet<>();
    for (SequenceFlow sequenceFlow : sequenceFlowCollection) {
      String targetActivityId = sequenceFlow.getTarget().getId();
      if(activitiesWithOneIncomingSequenceFlow.contains(targetActivityId) ){
        activityIdsWithMultipleIncomingSequenceFlows.add(targetActivityId);
      } else {
        activitiesWithOneIncomingSequenceFlow.add(targetActivityId);
      }
    }
    return activityIdsWithMultipleIncomingSequenceFlows;
  }
}
