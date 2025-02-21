/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.writer;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withOperateRuntimeException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationWriter extends AbstractBatchOperationWriter {

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private PermissionsService permissionsService;

  @Autowired private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired private PersistOperationHelper persistOperationHelper;

  @Override
  protected int addOperations(
      final CreateBatchOperationRequestDto batchOperationRequest,
      final BatchOperationEntity batchOperation)
      throws IOException {
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();
    Query query =
        openSearchQueryHelper.createProcessInstancesQuery(batchOperationRequest.getQuery());
    if (permissionsService.permissionsEnabled()) {
      final IdentityPermission permission =
          batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE
              ? IdentityPermission.DELETE_PROCESS_INSTANCE
              : IdentityPermission.UPDATE_PROCESS_INSTANCE;
      final var allowed = permissionsService.getProcessesWithPermission(permission);
      final var permissionQuery =
          allowed.isAll()
              ? matchAll()
              : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
      query = constantScore(withTenantCheck(and(query, permissionQuery)));
    }
    final RequestDSL.QueryType queryType =
        batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE
            ? ALL
            : ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, queryType)
            .query(query)
            .size(batchSize)
            .source(
                sourceInclude(
                    OperationTemplate.PROCESS_INSTANCE_KEY,
                    OperationTemplate.PROCESS_DEFINITION_KEY,
                    OperationTemplate.BPMN_PROCESS_ID));

    final AtomicInteger operationsCount = new AtomicInteger();

    final Consumer<List<Hit<ProcessInstanceSource>>> hitsConsumer =
        hits ->
            withOperateRuntimeException(
                () -> {
                  final List<ProcessInstanceSource> processInstanceSources =
                      hits.stream().map(Hit::source).toList();
                  return operationsCount.addAndGet(
                      persistOperationHelper.persistOperations(
                          processInstanceSources,
                          batchOperation.getId(),
                          batchOperationRequest,
                          null));
                });

    final Consumer<HitsMetadata<ProcessInstanceSource>> hitsMetadataConsumer =
        hitsMeta -> {
          validateTotalHits(hitsMeta);
          batchOperation.setInstancesCount((int) hitsMeta.total().value());
        };

    richOpenSearchClient
        .doc()
        .unsafeScrollWith(
            searchRequestBuilder,
            hitsConsumer,
            hitsMetadataConsumer,
            ProcessInstanceSource.class,
            false);

    return operationsCount.get();
  }

  private void validateTotalHits(final HitsMetadata<?> hitsMeta) {
    final long totalHits = hitsMeta.total().value();
    final Long maxSize = operateProperties.getBatchOperationMaxSize();
    if (maxSize != null && totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(
          String.format(
              "Too many process instances are selected for batch operation. Maximum possible amount: %s",
              maxSize));
    }
  }
}
