/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchVariableDaoV1")
public class ElasticsearchVariableDao extends ElasticsearchDao<Variable> implements VariableDao {

  @Autowired private VariableTemplate variableIndex;

  @Override
  protected void buildFiltering(
      final Query<Variable> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final var filter = query.getFilter();

    if (filter == null) {
      final var finalQuery =
          isTenantAware
              ? tenantHelper.makeQueryTenantAware(ElasticsearchUtil.matchAllQuery())
              : ElasticsearchUtil.matchAllQuery();
      searchRequestBuilder.query(finalQuery);
      return;
    }

    final var keyQ = buildIfPresent(Variable.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(Variable.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var processInstanceKeyQ =
        buildIfPresent(
            Variable.PROCESS_INSTANCE_KEY,
            filter.getProcessInstanceKey(),
            ElasticsearchUtil::termsQuery);

    final var scopeKeyQ =
        buildIfPresent(Variable.SCOPE_KEY, filter.getScopeKey(), ElasticsearchUtil::termsQuery);

    final var nameQ =
        buildIfPresent(Variable.NAME, filter.getName(), ElasticsearchUtil::termsQuery);

    final var valueQ =
        buildIfPresent(Variable.VALUE, filter.getValue(), ElasticsearchUtil::termsQuery);

    final var truncatedQ =
        buildIfPresent(Variable.TRUNCATED, filter.getTruncated(), ElasticsearchUtil::termsQuery);

    final var andOfAllQ =
        ElasticsearchUtil.joinWithAnd(
            keyQ, tenantIdQ, processInstanceKeyQ, scopeKeyQ, nameQ, valueQ, truncatedQ);

    final var finalQuery = isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQ) : andOfAllQ;

    searchRequestBuilder.query(finalQuery);
  }

  @Override
  public Variable byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<Variable> variables;
    try {
      final var query = ElasticsearchUtil.termsQuery(Variable.KEY, key);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchReq =
          new SearchRequest.Builder()
              .index(variableIndex.getAlias())
              .query(tenantAwareQuery)
              .build();

      variables =
          esClient.search(searchReq, ElasticsearchUtil.MAP_CLASS).hits().hits().stream()
              .map(Hit::source)
              .filter(Objects::nonNull)
              .map(src -> postProcessVariableDocument(src, true))
              .toList();

    } catch (final Exception e) {
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
    final var searchReqBuilder =
        buildQueryOn(query, Variable.KEY, new SearchRequest.Builder(), true);
    try {
      final var searchReq = searchReqBuilder.index(variableIndex.getAlias()).build();

      final var results = searchWithResultsReturn(searchReq, ElasticsearchUtil.MAP_CLASS);
      final var processedVariables =
          results.getItems().stream().map(src -> postProcessVariableDocument(src, false)).toList();

      return new Results<Variable>()
          .setItems(processedVariables)
          .setTotal(results.getTotal())
          .setSortValues(results.getSortValues());

    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected Variable postProcessVariableDocument(
      final Map<String, Object> varMap, final boolean isFullValue) {
    final var var =
        new Variable()
            .setKey((Long) varMap.get(Variable.KEY))
            .setProcessInstanceKey((Long) varMap.get(Variable.PROCESS_INSTANCE_KEY))
            .setScopeKey((Long) varMap.get(Variable.SCOPE_KEY))
            .setTenantId((String) varMap.get(Variable.TENANT_ID))
            .setName((String) varMap.get(Variable.NAME))
            .setValue((String) varMap.get(Variable.VALUE))
            .setTruncated((Boolean) varMap.get(Variable.TRUNCATED));

    if (isFullValue) {
      final String fullValue = (String) varMap.get(Variable.FULL_VALUE);
      if (fullValue != null) {
        var.setValue(fullValue);
      }
      var.setTruncated(false);
    }
    return var;
  }
}
