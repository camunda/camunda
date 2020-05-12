/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.reader;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.tasklist.entities.ActivityInstanceEntity;
import io.zeebe.tasklist.es.schema.templates.ActivityInstanceTemplate;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ActivityInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(ActivityInstanceReader.class);

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;

  public List<ActivityInstanceEntity> getAllActivityInstances(Long workflowInstanceKey) {
    final TermQueryBuilder workflowInstanceKeyQuery = termQuery(ActivityInstanceTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey);
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(activityInstanceTemplate)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(workflowInstanceKeyQuery))
        .sort(ActivityInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, ActivityInstanceEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all activity instances: %s", e.getMessage());
      logger.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

}
