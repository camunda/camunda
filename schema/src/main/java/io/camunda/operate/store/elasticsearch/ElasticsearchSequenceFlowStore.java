/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchSequenceFlowStore implements SequenceFlowStore {
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSequenceFlowStore.class);
  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(sequenceFlowTemplate, ElasticsearchUtil.QueryType.ALL)
        .source(new SearchSourceBuilder()
            .query(query)
            .sort(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.ASC));
    try {
      return tenantAwareClient.search(searchRequest, () -> {
        return ElasticsearchUtil.scroll(searchRequest, SequenceFlowEntity.class, objectMapper, esClient);
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining sequence flows: %s for processInstanceKey %s", e.getMessage(),processInstanceKey);
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
