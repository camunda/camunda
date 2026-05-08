/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.ActivatedJobResult;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;

public class JobActivationRequestResponseObserver implements ResponseObserver<JobActivationResult> {
  protected List<ActivatedJobResult> jobs = new ArrayList<>();
  protected CompletableFuture<ResponseEntity<Object>> result;

  private Runnable cancelationHandler;

  public JobActivationRequestResponseObserver(
      final CompletableFuture<ResponseEntity<Object>> result) {
    this.result = result;
  }

  @Override
  public void onCompleted() {
    result.complete(ResponseEntity.ok(JobActivationResult.Builder.create().jobs(jobs).build()));
  }

  @Override
  public void onNext(final JobActivationResult element) {
    if (element.getJobs() != null && !element.getJobs().isEmpty()) {
      jobs.addAll(element.getJobs());
    }
  }

  @Override
  public boolean isCancelled() {
    // the CompletableFuture will be canceled eventually if the request is canceled
    return result.isCancelled() || result.isCompletedExceptionally();
  }

  @Override
  public void onError(final Throwable throwable) {
    result.complete(RestErrorMapper.mapErrorToResponse(ErrorMapper.mapError(throwable)));
  }

  public void setCancelationHandler(final Runnable handler) {
    cancelationHandler = handler;
  }

  public void invokeCancelationHandler() {
    if (cancelationHandler != null) {
      cancelationHandler.run();
    }
  }
}
