/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.List;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class SequenceFlowReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowReader.class);

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  public List<SequenceFlowEntity> getSequenceFlowsByWorkflowInstanceKey(Long workflowInstanceKey) {
    final TermQueryBuilder workflowInstanceKeyQuery = termQuery(VariableTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(workflowInstanceKeyQuery);

    final SearchRequest searchRequest = new SearchRequest(sequenceFlowTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.ASC));
    try {
      return scroll(searchRequest, SequenceFlowEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining sequence flows: %s for workflowInstanceKey %s", e.getMessage(),workflowInstanceKey);
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
