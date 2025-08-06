/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.entities.BatchOperationEntity.getBatchOperationKey;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.BATCH_OPERATION_READ_AUTHORIZATION;

import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResumeBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSuspendBatchOperationRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
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
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.batchOperationSearchClient = batchOperationSearchClient;
  }

  @Override
  public BatchOperationServices withAuthentication(final CamundaAuthentication authentication) {
    return new BatchOperationServices(
        brokerClient,
        securityContextProvider,
        batchOperationSearchClient,
        authentication,
        executorProvider);
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> search(final BatchOperationQuery query) {
    return executeSearchRequest(
        () ->
            batchOperationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, BATCH_OPERATION_READ_AUTHORIZATION))
                .searchBatchOperations(query));
  }

  public SearchQueryResult<BatchOperationItemEntity> searchItems(
      final BatchOperationItemQuery query) {
    return executeSearchRequest(
        () ->
            batchOperationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, BATCH_OPERATION_READ_AUTHORIZATION))
                .searchBatchOperationItems(query));
  }

  public BatchOperationEntity getById(final String batchOperationKey) {
    return executeSearchRequest(
        () ->
            batchOperationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(BATCH_OPERATION_READ_AUTHORIZATION, batchOperationKey)))
                .getBatchOperation(batchOperationKey));
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> cancel(
      final String batchOperationKey) {
    LOGGER.debug("Cancelling batch operation with key '{}'", batchOperationKey);

    final var brokerRequest =
        new BrokerCancelBatchOperationRequest()
            .setBatchOperationKey(getBatchOperationKey(batchOperationKey));

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> suspend(
      final String batchOperationKey) {
    LOGGER.debug("Suspending batch operation with key '{}'", batchOperationKey);

    final var brokerRequest =
        new BrokerSuspendBatchOperationRequest()
            .setBatchOperationKey(getBatchOperationKey(batchOperationKey));

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationLifecycleManagementRecord> resume(
      final String batchOperationKey) {
    LOGGER.debug("Resuming batch operation with key '{}'", batchOperationKey);

    final var brokerRequest =
        new BrokerResumeBatchOperationRequest()
            .setBatchOperationKey(getBatchOperationKey(batchOperationKey));

    return sendBrokerRequest(brokerRequest);
  }
}
