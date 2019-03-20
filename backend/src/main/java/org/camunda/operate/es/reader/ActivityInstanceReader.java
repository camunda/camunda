/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ActivityInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(ActivityInstanceReader.class);

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;


  public ActivityInstanceTreeDto getActivityInstanceTree(ActivityInstanceTreeRequestDto requestDto) {

    List<ActivityInstanceEntity> activityInstances = getAllActivityInstances(requestDto.getWorkflowInstanceId());

    final Map<String, ActivityInstanceDto> nodes = ActivityInstanceDto.createMapFrom(activityInstances);

    ActivityInstanceTreeDto tree = new ActivityInstanceTreeDto();

    for (ActivityInstanceDto node: nodes.values()) {
      if (node.getParentId() != null) {
        if (node.getParentId().equals(requestDto.getWorkflowInstanceId())) {
          tree.getChildren().add(node);
        } else {
          nodes.get(node.getParentId()).getChildren().add(node);
        }
        if (node.getState().equals(ActivityState.INCIDENT)) {
          propagateState(node, nodes);
        }
      }
    }

    return tree;
  }

  private void propagateState(ActivityInstanceDto currentNode, Map<String, ActivityInstanceDto> allNodes) {
    if (currentNode.getParentId() != null) {
      final ActivityInstanceDto parent = allNodes.get(currentNode.getParentId());
      if (parent != null) {
        parent.setState(ActivityState.INCIDENT);
        propagateState(parent, allNodes);
      }
    }
  }

  public List<ActivityInstanceEntity> getAllActivityInstances(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ActivityInstanceTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(activityInstanceTemplate.getAlias())
        .setQuery(constantScoreQuery(workflowInstanceIdQ))
        .addSort(ActivityInstanceTemplate.POSITION, SortOrder.ASC);
    return scroll(requestBuilder, ActivityInstanceEntity.class);
  }

}
