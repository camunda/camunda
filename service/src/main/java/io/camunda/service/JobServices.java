/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class JobServices<T> extends ApiServices<JobServices<T>> {

  private final ActivateJobsHandler<T> activateJobsHandler;

  public JobServices(
      final BrokerClient brokerClient,
      final ActivateJobsHandler<T> activateJobsHandler,
      final CamundaSearchClient dataStoreClient) {
    this(brokerClient, activateJobsHandler, dataStoreClient, null, null);
  }

  public JobServices(
      final BrokerClient brokerClient,
      final ActivateJobsHandler<T> activateJobsHandler,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
    this.activateJobsHandler = activateJobsHandler;
  }

  @Override
  public JobServices<T> withAuthentication(final Authentication authentication) {
    return new JobServices<>(
        brokerClient, activateJobsHandler, searchClient, transformers, authentication);
  }

  public void activateJobs(
      final ActivateJobsRequest request,
      final ResponseObserver<T> responseObserver,
      final Consumer<Runnable> cancelationHandlerConsumer) {
    final var brokerRequest =
        new BrokerActivateJobsRequest(request.type())
            .setMaxJobsToActivate(request.maxJobsToActivate())
            .setTenantIds(request.tenantIds())
            .setTimeout(request.timeout())
            .setWorker(request.worker())
            .setVariables(request.fetchVariable());
    activateJobsHandler.activateJobs(
        brokerRequest, responseObserver, cancelationHandlerConsumer, request.requestTimeout());
  }

  public CompletableFuture<JobRecord> failJob(
      final long jobKey,
      final int retries,
      final String errorMessage,
      final Long retryBackOff,
      final Map<String, Object> variables) {
    final var request =
        new BrokerFailJobRequest(jobKey, retries, retryBackOff)
            .setVariables(getDocumentOrEmpty(variables))
            .setErrorMessage(errorMessage);
    return sendBrokerRequest(request);
  }

  public record ActivateJobsRequest(
      String type,
      int maxJobsToActivate,
      List<String> tenantIds,
      long timeout,
      String worker,
      List<String> fetchVariable,
      long requestTimeout) {}
}
