package org.camunda.optimize.service.es.reader;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

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
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private DateFilterHelper dateFilterHelper;

  public BranchAnalysisDto branchAnalysis(BranchAnalysisQueryDto request) {
    ValidationHelper.validate(request);
    logger.debug("Performing branch analysis on process definition: {}", request.getProcessDefinitionId());
    
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
    BoolQueryBuilder query = boolQuery()
      .must(termQuery("processDefinitionId", request.getProcessDefinitionId()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd())
      );

    return executeQuery(request, query);
  }

  private long calculateActivityCount(String activityId, BranchAnalysisQueryDto request) {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery("processDefinitionId", request.getProcessDefinitionId()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));

    return executeQuery(request, query);
  }

  private NestedQueryBuilder createMustMatchActivityIdQuery(String activityId) {
    return nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery("events.activityId", activityId),
      ScoreMode.None
    );
  }

  private long executeQuery(BranchAnalysisQueryDto request, BoolQueryBuilder query) {
    if (request.getFilter() != null) {
      query = dateFilterHelper.addFilters(query, request.getFilter());
    }

    SearchResponse sr = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessInstanceType())
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
