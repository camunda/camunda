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
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.SequenceFlowDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchSequenceFlowDaoV1")
public class ElasticsearchSequenceFlowDao extends ElasticsearchDao<SequenceFlow>
    implements SequenceFlowDao {

  @Autowired private SequenceFlowTemplate sequenceFlowIndex;

  @Override
  protected void buildFiltering(
      final Query<SequenceFlow> query,
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

    final var idQ = buildIfPresent(SequenceFlow.ID, filter.getId(), ElasticsearchUtil::termsQuery);

    final var activityIdQ =
        buildIfPresent(
            SequenceFlow.ACTIVITY_ID, filter.getActivityId(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(SequenceFlow.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var processInstanceKeyQ =
        buildIfPresent(
            SequenceFlow.PROCESS_INSTANCE_KEY,
            filter.getProcessInstanceKey(),
            ElasticsearchUtil::termsQuery);

    // Combine all queries with AND
    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(idQ, activityIdQ, tenantIdQ, processInstanceKeyQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  @Override
  public Results<SequenceFlow> search(final Query<SequenceFlow> query) throws APIException {
    logger.debug("search {}", query);
    final var searchReqBuilder =
        buildQueryOn(query, SequenceFlow.ID, new SearchRequest.Builder(), true);
    try {
      final var searchReq = searchReqBuilder.index(sequenceFlowIndex.getAlias()).build();

      return searchWithResultsReturn(searchReq, SequenceFlow.class);
    } catch (final Exception e) {
      throw new ServerException("Error in reading sequence flows", e);
    }
  }
}
