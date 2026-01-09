/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.IncidentDao;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchIncidentDaoV1")
public class ElasticsearchIncidentDao extends ElasticsearchDao<Incident> implements IncidentDao {

  @Autowired private IncidentTemplate incidentIndex;

  @Override
  protected void buildFiltering(
      final Query<Incident> query,
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

    final var keyQ = buildIfPresent(Incident.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var procDefKeyQ =
        buildIfPresent(
            Incident.PROCESS_DEFINITION_KEY,
            filter.getProcessDefinitionKey(),
            ElasticsearchUtil::termsQuery);

    final var procInstKeyQ =
        buildIfPresent(
            Incident.PROCESS_INSTANCE_KEY,
            filter.getProcessInstanceKey(),
            ElasticsearchUtil::termsQuery);

    final var typeQ =
        buildIfPresent(Incident.TYPE, filter.getType(), ElasticsearchUtil::termsQuery);

    // should be a match query
    final var messageQ =
        buildIfPresent(
            Incident.MESSAGE,
            filter.getMessage(),
            (field, value) -> QueryBuilders.match(m -> m.field(field).query(value)));

    final var stateQ =
        buildIfPresent(Incident.STATE, filter.getState(), ElasticsearchUtil::termsQuery);

    final var jobKeyQ =
        buildIfPresent(Incident.JOB_KEY, filter.getJobKey(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(Incident.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var creationTimeQ =
        buildIfPresent(Incident.CREATION_TIME, filter.getCreationTime(), this::buildMatchDateQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            keyQ,
            procDefKeyQ,
            procInstKeyQ,
            typeQ,
            messageQ,
            stateQ,
            jobKeyQ,
            tenantIdQ,
            creationTimeQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  @Override
  public Incident byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<Incident> incidents;
    try {
      final var query = ElasticsearchUtil.termsQuery(IncidentTemplate.KEY, key);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
      final var searchReqBuilder =
          new SearchRequest.Builder().index(incidentIndex.getAlias()).query(tenantAwareQuery);

      incidents =
          ElasticsearchUtil.scrollAllStream(
                  es8Client, searchReqBuilder, ElasticsearchUtil.MAP_CLASS)
              .flatMap(res -> res.hits().hits().stream())
              .map(Hit::source)
              .filter(Objects::nonNull)
              .map(this::createIncidentFromIncidentMap)
              .toList();

    } catch (final Exception e) {
      throw new ServerException(String.format("Error in reading incident for key %s", key), e);
    }
    if (incidents.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No incident found for key %s ", key));
    }
    if (incidents.size() > 1) {
      throw new ServerException(String.format("Found more than one incidents for key %s", key));
    }
    return incidents.get(0);
  }

  @Override
  public Results<Incident> search(final Query<Incident> query) throws APIException {
    logger.debug("search {}", query);
    mapFieldsInSort(query);

    final var searchReqBuilder =
        buildQueryOn(query, Incident.KEY, new SearchRequest.Builder(), true);

    try {
      final var searchReq = searchReqBuilder.index(incidentIndex.getAlias()).build();

      final var results = searchWithResultsReturn(searchReq, ElasticsearchUtil.MAP_CLASS);

      return new Results<Incident>()
          .setSortValues(results.getSortValues())
          .setTotal(results.getTotal())
          .setItems(results.getItems().stream().map(this::createIncidentFromIncidentMap).toList());
    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  private void mapFieldsInSort(final Query<Incident> query) {
    if (query.getSort() == null) {
      return;
    }
    query.setSort(
        query.getSort().stream()
            .map(
                s ->
                    s.setField(
                        Incident.OBJECT_TO_SEARCH_MAP.getOrDefault(s.getField(), s.getField())))
            .collect(Collectors.toList()));
  }

  protected Incident createIncidentFromIncidentMap(final Map<String, Object> map) {
    return new Incident()
        .setKey((Long) map.get(IncidentTemplate.KEY))
        .setProcessInstanceKey((Long) map.get(IncidentTemplate.PROCESS_INSTANCE_KEY))
        .setProcessDefinitionKey((Long) map.get(IncidentTemplate.PROCESS_DEFINITION_KEY))
        .setType((String) map.get(IncidentTemplate.ERROR_TYPE))
        .setMessage((String) map.get(IncidentTemplate.ERROR_MSG))
        .setCreationTime(
            dateTimeFormatter.convertGeneralToApiDateTime((String) map.get(Incident.CREATION_TIME)))
        .setState((String) map.get(Incident.STATE))
        .setJobKey((Long) map.get(Incident.JOB_KEY))
        .setTenantId((String) map.get(Incident.TENANT_ID));
  }
}
