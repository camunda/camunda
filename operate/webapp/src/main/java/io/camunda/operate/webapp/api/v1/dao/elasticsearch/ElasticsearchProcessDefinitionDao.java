/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessDefinitionDaoV1")
public class ElasticsearchProcessDefinitionDao extends ElasticsearchDao<ProcessDefinition>
    implements ProcessDefinitionDao {

  @Autowired private ProcessIndex processIndex;

  @Override
  public Results<ProcessDefinition> search(final Query<ProcessDefinition> query)
      throws APIException {
    logger.debug("search {}", query);
    final var searchReqBuilder =
        buildQueryOn(query, ProcessDefinition.KEY, new SearchRequest.Builder(), true);

    try {
      final var searchReq =
          searchReqBuilder
              .index(processIndex.getAlias())
              .source(s -> s.filter(f -> f.excludes(BPMN_XML)))
              .build();

      return searchWithResultsReturn(searchReq, ProcessDefinition.class);
    } catch (final Exception e) {
      throw new ServerException("Error in reading process definitions", e);
    }
  }

  @Override
  public ProcessDefinition byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessDefinition> processDefinitions;
    try {
      final var searchReqBuilder = processDefinitionKeySearchReq(key);

      processDefinitions =
          ElasticsearchUtil.scrollAllStream(esClient, searchReqBuilder, ProcessDefinition.class)
              .flatMap(res -> res.hits().hits().stream())
              .map(Hit::source)
              .toList();

    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading process definition for key %s", key), e);
    }
    if (processDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process definition found for key %s ", key));
    }
    if (processDefinitions.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process definition for key %s", key));
    }
    return processDefinitions.get(0);
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final var searchReqBuilder =
          processDefinitionKeySearchReq(key).source(s -> s.filter(f -> f.includes(BPMN_XML)));

      final var res = esClient.search(searchReqBuilder.build(), ElasticsearchUtil.MAP_CLASS);

      if (res.hits().total().value() == 1) {
        return (String) res.hits().hits().get(0).source().get(BPMN_XML);
      }
    } catch (final IOException e) {
      throw new ServerException(
          String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Process definition for key %s not found.", key));
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessDefinition> query,
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

    final var nameQ =
        buildIfPresent(ProcessDefinition.NAME, filter.getName(), ElasticsearchUtil::termsQuery);

    final var bpmnProcessIdQ =
        buildIfPresent(
            ProcessDefinition.BPMN_PROCESS_ID,
            filter.getBpmnProcessId(),
            ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            ProcessDefinition.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var versionQ =
        buildIfPresent(
            ProcessDefinition.VERSION, filter.getVersion(), ElasticsearchUtil::termsQuery);

    final var versionTagQ =
        buildIfPresent(
            ProcessDefinition.VERSION_TAG, filter.getVersionTag(), ElasticsearchUtil::termsQuery);

    final var keyQ =
        buildIfPresent(ProcessDefinition.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            nameQ, bpmnProcessIdQ, tenantIdQ, versionQ, versionTagQ, keyQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  private SearchRequest.Builder processDefinitionKeySearchReq(final Long key) {
    final var query = ElasticsearchUtil.termsQuery(ProcessIndex.KEY, key);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    return new SearchRequest.Builder().index(processIndex.getAlias()).query(tenantAwareQuery);
  }
}
