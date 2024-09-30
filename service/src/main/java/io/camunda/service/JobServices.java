/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRequest;
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
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.activateJobsHandler = activateJobsHandler;
  }

  @Override
  public JobServices<T> withAuthentication(final Authentication authentication) {
    return new JobServices<>(brokerClient, activateJobsHandler, authentication);
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

  public CompletableFuture<JobRecord> errorJob(
      final long jobKey,
      final String errorCode,
      final String errorMessage,
      final Map<String, Object> variables) {
    final var request =
        new BrokerThrowErrorRequest(jobKey, errorCode)
            .setErrorMessage(errorMessage)
            .setVariables(getDocumentOrEmpty(variables));
    return sendBrokerRequest(request);
  }

  public CompletableFuture<JobRecord> completeJob(
      final long jobKey, final Map<String, Object> variables) {
    return sendBrokerRequest(new BrokerCompleteJobRequest(jobKey, getDocumentOrEmpty(variables)));
  }

  public CompletableFuture<JobRecord> updateJob(
      final long jobKey, final UpdateJobChangeset changeset) {
    return sendBrokerRequest(
        new BrokerUpdateJobRequest(jobKey, changeset.retries(), changeset.timeout()));
  }

  public record ActivateJobsRequest(
      String type,
      int maxJobsToActivate,
      List<String> tenantIds,
      long timeout,
      String worker,
      List<String> fetchVariable,
      long requestTimeout) {}

  public record UpdateJobChangeset(Integer retries, Long timeout) {}
}
