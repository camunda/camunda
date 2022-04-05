/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchVariableDaoV1")
public class ElasticsearchVariableDao extends ElasticsearchDao<Variable> implements VariableDao {

  @Autowired
  private VariableTemplate variableIndex;

  @Override
  protected void buildFiltering(final Query<Variable> query, final SearchSourceBuilder searchSourceBuilder) {
    final Variable filter = query.getFilter();
    List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(Variable.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      queryBuilders.add(buildTermQuery(Variable.SCOPE_KEY, filter.getScopeKey()));
      queryBuilders.add(buildTermQuery(Variable.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(Variable.VALUE, filter.getValue()));
      queryBuilders.add(buildTermQuery(Variable.TRUNCATED, filter.getTruncated()));
    }
    searchSourceBuilder.query(
        joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
  }

  @Override
  public Variable byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    List<Variable> variables;
    try {
      variables = searchFor(new SearchSourceBuilder().query(termQuery(Variable.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading variable for key %s", key), e);
    }
    if (variables.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No variable found for key %s ", key));
    }
    if (variables.size() > 1) {
      throw new ServerException(String.format("Found more than one variables for key %s", key));
    }
    return variables.get(0);
  }

  @Override
  public Results<Variable> search(final Query<Variable> query) throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder = buildQueryOn(
        query,
        Variable.KEY,
        new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(
              variableIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest,
          RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<Variable> variables =
            ElasticsearchUtil.mapSearchHits(searchHitArray,
                this::searchHitToVariableWithoutFullValue);
        return new Results<Variable>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(variables)
            .setSortValues(sortValues);
      } else {
        return new Results<Variable>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected Variable searchHitToVariableWithoutFullValue(final SearchHit searchHit){
    return searchHitToVariable(searchHit, false);
  }

  protected Variable searchHitToVariableWithFullValue(final SearchHit searchHit){
    return searchHitToVariable(searchHit, true);
  }

  protected Variable searchHitToVariable(final SearchHit searchHit, final boolean isFullValue) {
    final Map<String, Object> searchHitAsMap = searchHit.getSourceAsMap();
    final Variable variable = new Variable()
        .setKey((Long) searchHitAsMap.get(Variable.KEY))
        .setProcessInstanceKey((Long) searchHitAsMap.get(Variable.PROCESS_INSTANCE_KEY))
        .setScopeKey((Long) searchHitAsMap.get(Variable.SCOPE_KEY))
        .setName((String) searchHitAsMap.get(Variable.NAME))
        .setValue((String) searchHitAsMap.get(Variable.VALUE))
        .setTruncated((Boolean) searchHitAsMap.get(Variable.TRUNCATED));
    if (isFullValue) {
      final String fullValue = (String) searchHitAsMap.get(Variable.FULL_VALUE);
      if (fullValue != null) {
        variable.setValue(fullValue);
      }
      variable.setTruncated(false);
    }
    return variable;
  }

  protected List<Variable> searchFor(final SearchSourceBuilder searchSourceBuilder){
    try {
      final SearchRequest searchRequest = new SearchRequest(variableIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        return ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToVariableWithFullValue);
      } else {
        return List.of();
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading variables", e);
    }
  }
}

