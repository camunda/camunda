/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

public class JobActivationRequestResponseObserver implements ResponseObserver<JobActivationResult> {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobActivationRequestResponseObserver.class);

  protected JobActivationResult response = new JobActivationResult();
  protected CompletableFuture<ResponseEntity<Object>> result;

  private Runnable cancelationHandler;
  private volatile HttpServletResponse servletResponse;

  public JobActivationRequestResponseObserver(
      final CompletableFuture<ResponseEntity<Object>> result) {
    this.result = result;
  }

  /**
   * Sets the servlet response for connection probing. When set, {@link #onNext} will attempt a
   * write+flush to the response before accepting activated jobs. If the write fails (client
   * disconnected), an exception is thrown which causes the caller ({@code
   * InflightActivateJobsRequest.tryToSendActivatedJobs}) to trigger job reactivation.
   */
  public void setServletResponse(final HttpServletResponse servletResponse) {
    this.servletResponse = servletResponse;
  }

  @Override
  public void onCompleted() {
    result.complete(ResponseEntity.ok(response));
  }

  @Override
  public void onNext(final JobActivationResult element) {
    // Probe the connection before accepting jobs. If the client is gone (TCP RST received),
    // the flush throws IOException. This exception propagates to tryToSendActivatedJobs()
    // which catches it and triggers reactivateJobs() to return the jobs to the broker.
    probeConnectionOrThrow();

    if (element.getJobs() != null && !element.getJobs().isEmpty()) {
      element.getJobs().forEach(response::addJobsItem);
    }
  }

  @Override
  public boolean isCancelled() {
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

  private void probeConnectionOrThrow() {
    if (servletResponse == null) {
      return;
    }
    try {
      final var out = servletResponse.getOutputStream();
      out.write(' ');
      out.flush();
    } catch (final IOException e) {
      LOG.debug("Connection probe failed before job delivery ({}).", e.getMessage());
      throw new ClientDisconnectedException("Client disconnected", e);
    }
  }
}
