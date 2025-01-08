/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.writer;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.webapp.elasticsearch.QueryHelper;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.AbstractBatchOperationWriter;
import io.camunda.operate.webapp.writer.PersistOperationHelper;
import io.camunda.operate.webapp.writer.ProcessInstanceSource;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class BatchOperationWriter extends AbstractBatchOperationWriter {

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private PermissionsService permissionsService;

  @Autowired private QueryHelper queryHelper;

  @Autowired private PersistOperationHelper persistOperationHelper;

  @Override
  protected int addOperations(
      final CreateBatchOperationRequestDto batchOperationRequest,
      final BatchOperationEntity batchOperation)
      throws IOException {
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();
    ConstantScoreQueryBuilder query =
        queryHelper.createProcessInstancesQuery(batchOperationRequest.getQuery());
    if (permissionsService.permissionsEnabled()) {
      final IdentityPermission permission =
          batchOperationRequest.getOperationType().equals(OperationType.DELETE_PROCESS_INSTANCE)
              ? IdentityPermission.DELETE_PROCESS_INSTANCE
              : IdentityPermission.UPDATE_PROCESS_INSTANCE;
      final var allowed = permissionsService.getProcessesWithPermission(permission);
      final var permissionQuery =
          allowed.isAll()
              ? QueryBuilders.matchAllQuery()
              : QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
      query = constantScoreQuery(joinWithAnd(query, permissionQuery));
    }
    QueryType queryType = QueryType.ONLY_RUNTIME;
    if (batchOperationRequest.getOperationType().equals(OperationType.DELETE_PROCESS_INSTANCE)) {
      queryType = QueryType.ALL;
    }
    final String[] includeFields =
        new String[] {
          OperationTemplate.PROCESS_INSTANCE_KEY,
          OperationTemplate.PROCESS_DEFINITION_KEY,
          OperationTemplate.BPMN_PROCESS_ID
        };

    // Query the list of process instances that will be modified based on the input query
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .size(batchSize)
                    .fetchSource(includeFields, null));

    final AtomicInteger operationsCount = new AtomicInteger();
    tenantAwareClient.search(
        searchRequest,
        () -> {
          ElasticsearchUtil.scrollWith(
              searchRequest,
              esClient,
              searchHits -> {
                try {
                  final List<ProcessInstanceSource> processInstanceSources = new ArrayList<>();
                  for (final SearchHit hit : searchHits.getHits()) {
                    processInstanceSources.add(
                        ProcessInstanceSource.fromSourceMap(hit.getSourceAsMap()));
                  }
                  operationsCount.addAndGet(
                      persistOperationHelper.persistOperations(
                          processInstanceSources,
                          batchOperation.getId(),
                          batchOperationRequest,
                          null));
                } catch (final PersistenceException e) {
                  throw new RuntimeException(e);
                }
              },
              null,
              searchHits -> {
                validateTotalHits(searchHits);
                batchOperation.setInstancesCount((int) searchHits.getTotalHits().value);
              });
          return null;
        });
    return operationsCount.get();
  }

  private void validateTotalHits(final SearchHits hits) {
    final long totalHits = hits.getTotalHits().value;
    if (operateProperties.getBatchOperationMaxSize() != null
        && totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(
          String.format(
              "Too many process instances are selected for batch operation. Maximum possible amount: %s",
              operateProperties.getBatchOperationMaxSize()));
    }
  }
}
