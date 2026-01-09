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
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchFlowNodeInstanceDaoV1")
public class ElasticsearchFlowNodeInstanceDao extends ElasticsearchDao<FlowNodeInstance>
    implements FlowNodeInstanceDao {

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired private ProcessCache processCache;

  @Override
  protected void buildFiltering(
      final Query<FlowNodeInstance> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final FlowNodeInstance filter = query.getFilter();

    if (filter == null) {
      final var finalQuery =
          isTenantAware
              ? tenantHelper.makeQueryTenantAware(ElasticsearchUtil.matchAllQuery())
              : ElasticsearchUtil.matchAllQuery();
      searchRequestBuilder.query(finalQuery);
      return;
    }

    final var keyQ =
        buildIfPresent(FlowNodeInstance.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var processInstanceKeyQ =
        buildIfPresent(
            FlowNodeInstance.PROCESS_INSTANCE_KEY,
            filter.getProcessInstanceKey(),
            ElasticsearchUtil::termsQuery);

    final var processDefinitionKeyQ =
        buildIfPresent(
            FlowNodeInstance.PROCESS_DEFINITION_KEY,
            filter.getProcessDefinitionKey(),
            ElasticsearchUtil::termsQuery);

    final var startDateQ =
        buildIfPresent(
            FlowNodeInstance.START_DATE, filter.getStartDate(), this::buildMatchDateQuery);

    final var endDateQ =
        buildIfPresent(FlowNodeInstance.END_DATE, filter.getEndDate(), this::buildMatchDateQuery);

    final var stateQ =
        buildIfPresent(FlowNodeInstance.STATE, filter.getState(), ElasticsearchUtil::termsQuery);

    final var typeQ =
        buildIfPresent(FlowNodeInstance.TYPE, filter.getType(), ElasticsearchUtil::termsQuery);

    final var flowNodeIdQ =
        buildIfPresent(
            FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId(), ElasticsearchUtil::termsQuery);

    final var incidentQ =
        buildIfPresent(
            FlowNodeInstance.INCIDENT, filter.getIncident(), ElasticsearchUtil::termsQuery);

    final var incidentKeyQ =
        buildIfPresent(
            FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            FlowNodeInstance.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            keyQ,
            processInstanceKeyQ,
            processDefinitionKeyQ,
            startDateQ,
            endDateQ,
            stateQ,
            typeQ,
            flowNodeIdQ,
            incidentQ,
            incidentKeyQ,
            tenantIdQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  @Override
  public FlowNodeInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<FlowNodeInstance> flowNodeInstances;
    try {
      flowNodeInstances = searchFor(ElasticsearchUtil.termsQuery(FlowNodeInstance.KEY, key));
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading flownode instance for key %s", key), e);
    }
    if (flowNodeInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No flownode instance found for key %s ", key));
    }
    if (flowNodeInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one flownode instances for key %s", key));
    }
    return flowNodeInstances.get(0);
  }

  @Override
  public Results<FlowNodeInstance> search(final Query<FlowNodeInstance> query) throws APIException {
    logger.debug("search {}", query);
    final var searchReqBuilder =
        buildQueryOn(query, FlowNodeInstance.KEY, new SearchRequest.Builder(), true);

    try {
      final var searchReq = searchReqBuilder.index(flowNodeInstanceIndex.getAlias()).build();
      final var results = searchWithResultsReturn(searchReq, FlowNodeInstance.class);

      results.getItems().forEach(this::postProcessFlowNodeInstance);

      return results;

    } catch (final Exception e) {
      throw new ServerException("Error in reading flownode instances", e);
    }
  }

  private FlowNodeInstance postProcessFlowNodeInstance(final FlowNodeInstance flowNodeInstance) {
    flowNodeInstance.setStartDate(
        dateTimeFormatter.convertGeneralToApiDateTime(flowNodeInstance.getStartDate()));
    flowNodeInstance.setEndDate(
        dateTimeFormatter.convertGeneralToApiDateTime(flowNodeInstance.getEndDate()));

    if (flowNodeInstance.getFlowNodeId() != null) {
      final String flowNodeName =
          processCache.getFlowNodeNameOrDefaultValue(
              flowNodeInstance.getProcessDefinitionKey(), flowNodeInstance.getFlowNodeId(), null);
      flowNodeInstance.setFlowNodeName(flowNodeName);
    }

    return flowNodeInstance;
  }

  protected List<FlowNodeInstance> searchFor(
      final co.elastic.clients.elasticsearch._types.query_dsl.Query query) {
    try {
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var res =
          es8Client.search(
              s -> s.index(flowNodeInstanceIndex.getAlias()).query(tenantAwareQuery),
              FlowNodeInstance.class);

      return res.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .map(this::postProcessFlowNodeInstance)
          .toList();

    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }
}
