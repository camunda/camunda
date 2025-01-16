/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchSequenceFlowStore implements SequenceFlowStore {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSequenceFlowStore.class);
  @Autowired private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(
      final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(sequenceFlowTemplate, ElasticsearchUtil.QueryType.ALL)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.ASC));
    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest, SequenceFlowEntity.class, objectMapper, esClient);
          });
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining sequence flows: %s for processInstanceKey %s",
              e.getMessage(), processInstanceKey);
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
