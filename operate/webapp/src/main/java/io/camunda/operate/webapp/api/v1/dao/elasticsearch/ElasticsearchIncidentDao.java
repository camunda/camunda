/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchIncidentDaoV1")
public class ElasticsearchIncidentDao extends ElasticsearchDao<Incident> implements IncidentDao {

  @Autowired private IncidentTemplate incidentIndex;

  @Override
  protected void buildFiltering(
      final Query<Incident> query, final SearchSourceBuilder searchSourceBuilder) {
    final Incident filter = query.getFilter();
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(Incident.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(Incident.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(
          buildTermQuery(Incident.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      queryBuilders.add(buildTermQuery(Incident.TYPE, filter.getType()));
      queryBuilders.add(buildMatchQuery(Incident.MESSAGE, filter.getMessage()));
      queryBuilders.add(buildTermQuery(Incident.STATE, filter.getState()));
      queryBuilders.add(buildTermQuery(Incident.JOB_KEY, filter.getJobKey()));
      queryBuilders.add(buildTermQuery(Incident.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildMatchDateQuery(Incident.CREATION_TIME, filter.getCreationTime()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  @Override
  public Incident byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<Incident> incidents;
    try {
      incidents = searchFor(new SearchSourceBuilder().query(termQuery(IncidentTemplate.KEY, key)));
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
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, Incident.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(incidentIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        final List<Incident> incidents =
            ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToIncident);
        return new Results<Incident>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(incidents)
            .setSortValues(sortValues);
      } else {
        return new Results<Incident>().setTotal(searchHits.getTotalHits().value);
      }
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

  protected Incident searchHitToIncident(final SearchHit searchHit) {
    final Map<String, Object> searchHitAsMap = searchHit.getSourceAsMap();
    return new Incident()
        .setKey((Long) searchHitAsMap.get(IncidentTemplate.KEY))
        .setProcessInstanceKey((Long) searchHitAsMap.get(IncidentTemplate.PROCESS_INSTANCE_KEY))
        .setProcessDefinitionKey((Long) searchHitAsMap.get(IncidentTemplate.PROCESS_DEFINITION_KEY))
        .setType((String) searchHitAsMap.get(IncidentTemplate.ERROR_TYPE))
        .setMessage((String) searchHitAsMap.get(IncidentTemplate.ERROR_MSG))
        .setCreationTime(
            dateTimeFormatter.convertGeneralToApiDateTime(
                (String) searchHitAsMap.get(Incident.CREATION_TIME)))
        .setState((String) searchHitAsMap.get(Incident.STATE))
        .setJobKey((Long) searchHitAsMap.get(Incident.JOB_KEY))
        .setTenantId((String) searchHitAsMap.get(Incident.TENANT_ID));
  }

  protected List<Incident> searchFor(final SearchSourceBuilder searchSourceBuilder) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(incidentIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        return ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToIncident);
      } else {
        return List.of();
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }
}
