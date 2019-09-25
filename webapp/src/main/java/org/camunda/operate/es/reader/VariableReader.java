/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.rest.dto.VariableDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class VariableReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private OperationReader operationReader;

  public List<VariableDto> getVariables(Long workflowInstanceKey, Long scopeKey) {
    final TermQueryBuilder workflowInstanceKeyQuery = termQuery(VariableTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey);
    final TermQueryBuilder scopeKeyQuery = termQuery(VariableTemplate.SCOPE_KEY, scopeKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceKeyQuery, scopeKeyQuery));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(variableTemplate, ALL)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(VariableTemplate.NAME, SortOrder.ASC));
    try {
      final List<VariableEntity> variableEntities = scroll(searchRequest, VariableEntity.class);
      final Map<String, List<OperationEntity>> operations = operationReader.getOperationsPerVariableName(workflowInstanceKey, scopeKey);
      return VariableDto.createFrom(variableEntities, operations);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
