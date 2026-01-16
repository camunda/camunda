/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.*;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessInstanceDaoV1")
public class ElasticsearchProcessInstanceDao extends ElasticsearchDao<ProcessInstance>
    implements ProcessInstanceDao {

  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  private ProcessInstance postProcessProcessInstance(final ProcessInstance pi) {
    pi.setStartDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getStartDate()));
    pi.setEndDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getEndDate()));

    return pi;
  }

  @Override
  public Results<ProcessInstance> search(final Query<ProcessInstance> query) throws APIException {
    logger.debug("search {}", query);

    final var searchReqBuilder =
        buildQueryOn(query, ProcessInstance.KEY, new SearchRequest.Builder(), true);

    try {
      final var searchReq = searchReqBuilder.index(processInstanceIndex.getAlias()).build();

      final var results = searchWithResultsReturn(searchReq, ProcessInstance.class);
      results.getItems().forEach(this::postProcessProcessInstance);

      return results;
    } catch (final Exception e) {
      throw new ServerException("Error in reading process instances", e);
    }
  }

  @Override
  public ProcessInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessInstance> processInstances;
    try {
      final var query = ElasticsearchUtil.termsQuery(ListViewTemplate.KEY, key);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchReq =
          new SearchRequest.Builder()
              .index(processInstanceIndex.getAlias())
              .query(tenantAwareQuery);

      processInstances =
          ElasticsearchUtil.scrollAllStream(esClient, searchReq, ProcessInstance.class)
              .flatMap(res -> res.hits().hits().stream())
              .map(Hit::source)
              .filter(Objects::nonNull)
              .map(this::postProcessProcessInstance)
              .toList();
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading process instance for key %s", key), e);
    }
    if (processInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process instances found for key %s ", key));
    }
    if (processInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process instances for key %s", key));
    }
    return processInstances.get(0);
  }

  @Override
  public ChangeStatus delete(final Long key) throws APIException {
    // Check for not exists
    byKey(key);
    try {
      processInstanceWriter.deleteInstanceById(key);
      return new ChangeStatus()
          .setDeleted(1)
          .setMessage(
              String.format("Process instance and dependant data deleted for key '%s'", key));
    } catch (final IllegalArgumentException iae) {
      throw new ClientException(iae.getMessage(), iae);
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in deleting process instance and dependant data for key '%s'", key),
          e);
    }
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessInstance> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final var filter = query.getFilter();

    final var joinRelationQ =
        ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    if (filter == null) {
      final var finalQuery =
          isTenantAware ? tenantHelper.makeQueryTenantAware(joinRelationQ) : joinRelationQ;
      searchRequestBuilder.query(finalQuery);
      return;
    }

    final var keyQ =
        buildIfPresent(ProcessInstance.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var procDefKeyQ =
        buildIfPresent(
            ProcessInstance.PROCESS_DEFINITION_KEY,
            filter.getProcessDefinitionKey(),
            ElasticsearchUtil::termsQuery);

    final var parentKeyQ =
        buildIfPresent(
            ProcessInstance.PARENT_KEY, filter.getParentKey(), ElasticsearchUtil::termsQuery);

    final var parentFlowNodeKeyQ =
        buildIfPresent(
            ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY,
            filter.getParentFlowNodeInstanceKey(),
            ElasticsearchUtil::termsQuery);

    final var versionQ =
        buildIfPresent(
            ProcessInstance.VERSION, filter.getProcessVersion(), ElasticsearchUtil::termsQuery);

    final var versionTagQ =
        buildIfPresent(
            ProcessInstance.VERSION_TAG,
            filter.getProcessVersionTag(),
            ElasticsearchUtil::termsQuery);

    final var bpmnProcessIdQ =
        buildIfPresent(
            ProcessInstance.BPMN_PROCESS_ID,
            filter.getBpmnProcessId(),
            ElasticsearchUtil::termsQuery);

    final var stateQ =
        buildIfPresent(ProcessInstance.STATE, filter.getState(), ElasticsearchUtil::termsQuery);

    final var incidentQ =
        buildIfPresent(
            ProcessInstance.INCIDENT, filter.getIncident(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            ProcessInstance.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var startDateQ =
        buildIfPresent(
            ProcessInstance.START_DATE, filter.getStartDate(), this::buildMatchDateQuery);

    final var endDateQ =
        buildIfPresent(ProcessInstance.END_DATE, filter.getEndDate(), this::buildMatchDateQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            joinRelationQ,
            keyQ,
            procDefKeyQ,
            parentKeyQ,
            parentFlowNodeKeyQ,
            versionQ,
            versionTagQ,
            bpmnProcessIdQ,
            stateQ,
            incidentQ,
            tenantIdQ,
            startDateQ,
            endDateQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }
}
