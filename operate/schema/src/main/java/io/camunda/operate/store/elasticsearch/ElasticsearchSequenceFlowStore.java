/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchSequenceFlowStore implements SequenceFlowStore {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSequenceFlowStore.class);
  @Autowired private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired private ElasticsearchClient esClient;

  @Override
  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(
      final Long processInstanceKey) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(
                SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(sequenceFlowTemplate, QueryType.ALL))
            .query(query)
            .sort(ElasticsearchUtil.sortOrder(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.Asc));

    try {
      return ElasticsearchUtil.scrollAllStream(
              esClient, searchRequestBuilder, SequenceFlowEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();

    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining sequence flows: %s for processInstanceKey %s",
              e.getMessage(), processInstanceKey);
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
