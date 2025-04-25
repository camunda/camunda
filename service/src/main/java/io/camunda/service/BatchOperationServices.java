/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.batchOperationQuery;

import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.Authorization.Builder;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPauseBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResumeBatchOperationRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BatchOperationServices
    extends SearchQueryService<BatchOperationServices, BatchOperationQuery, BatchOperationEntity> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationServices.class);

  private final BatchOperationSearchClient batchOperationSearchClient;

  public BatchOperationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final BatchOperationSearchClient batchOperationSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.batchOperationSearchClient = batchOperationSearchClient;
  }

  @Override
  public BatchOperationServices withAuthentication(final Authentication authentication) {
    return new BatchOperationServices(
        brokerClient, securityContextProvider, batchOperationSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> search(final BatchOperationQuery query) {
    return batchOperationSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(Builder::read)))
        .searchBatchOperations(query);
  }

  public BatchOperationEntity getByKey(final Long key) {
    final var result =
        batchOperationSearchClient.searchBatchOperations(
            batchOperationQuery(q -> q.filter(f -> f.batchOperationKeys(key))));
    return getSingleResultOrThrow(result, key, "BatchOperation");
  }

  public List<BatchOperationItemEntity> getItemsByKey(final Long key) {
    return batchOperationSearchClient.getBatchOperationItems(key);
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> cancel(final long batchKey) {
    LOGGER.debug("Cancelling batch operation with key '{}'", batchKey);

    final var brokerRequest =
        new BrokerCancelBatchOperationRequest().setBatchOperationKey(batchKey);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> pause(final long batchKey) {
    LOGGER.debug("Pausing batch operation with key '{}'", batchKey);

    final var brokerRequest = new BrokerPauseBatchOperationRequest().setBatchOperationKey(batchKey);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> resume(final long batchKey) {
    LOGGER.debug("Resuming batch operation with key '{}'", batchKey);

    final var brokerRequest =
        new BrokerResumeBatchOperationRequest().setBatchOperationKey(batchKey);

    return sendBrokerRequest(brokerRequest);
  }
}
