/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.List;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class VariableReader extends AbstractReader {

  @Autowired
  private VariableTemplate variableTemplate;

  public List<VariableEntity> getVariables(String workflowInstanceId, String scopeId) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(VariableTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);
    final TermQueryBuilder scopeIdQ = termQuery(VariableTemplate.SCOPE_ID, scopeId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceIdQ, scopeIdQ));

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(variableTemplate.getAlias())
        .setQuery(query)
        .addSort(VariableTemplate.NAME, SortOrder.ASC);
    return scroll(requestBuilder, VariableEntity.class);
  }

}
