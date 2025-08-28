/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.JOB_READ_AUTHORIZATION;

import io.camunda.search.clients.JobSearchClient;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class JobServices<T> extends SearchQueryService<JobServices<T>, JobQuery, JobEntity> {

  private final ActivateJobsHandler<T> activateJobsHandler;
  private final JobSearchClient jobSearchClient;

  public JobServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ActivateJobsHandler<T> activateJobsHandler,
      final JobSearchClient jobSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.activateJobsHandler = activateJobsHandler;
    this.jobSearchClient = jobSearchClient;
  }

  @Override
  public JobServices<T> withAuthentication(final CamundaAuthentication authentication) {
    return new JobServices<>(
        brokerClient,
        securityContextProvider,
        activateJobsHandler,
        jobSearchClient,
        authentication,
        executorProvider);
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
    brokerRequest.setAuthorization(authentication.claims());
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
      final long jobKey, final Map<String, Object> variables, final JobResult result) {
    return sendBrokerRequest(
        new BrokerCompleteJobRequest(jobKey, getDocumentOrEmpty(variables), result));
  }

  public CompletableFuture<JobRecord> updateJob(
      final long jobKey, final Long operationReference, final UpdateJobChangeset changeset) {
    final var brokerRequest =
        new BrokerUpdateJobRequest(jobKey, changeset.retries(), changeset.timeout());
    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }
    return sendBrokerRequest(brokerRequest);
  }

  @Override
  public SearchQueryResult<JobEntity> search(final JobQuery query) {
    return executeSearchRequest(
        () ->
            jobSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, JOB_READ_AUTHORIZATION))
                .searchJobs(query));
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
