/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.mapper.ProbeMapper;
import io.camunda.gateway.protocol.model.ActivatedProbeResult;
import io.camunda.gateway.protocol.model.ProbeActivationResult;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

/**
 * Answers a worker's poll for probes, as {@link JobActivationRequestResponseObserver} answers a
 * poll for jobs. The poll counts as cancelled once its response future is cancelled, which the
 * controller does when the worker's request is gone; the long polling handler then yields the
 * probes it could not deliver (D41).
 */
@NullMarked
final class ProbeActivationResponseObserver implements ResponseObserver<ProbeBatchRecord> {

  private final CompletableFuture<ResponseEntity<Object>> result;
  private final String physicalTenantId;
  private final List<ActivatedProbeResult> probes = new ArrayList<>();
  private @Nullable Runnable cancelationHandler;

  ProbeActivationResponseObserver(
      final CompletableFuture<ResponseEntity<Object>> result, final String physicalTenantId) {
    this.result = result;
    this.physicalTenantId = physicalTenantId;
  }

  /**
   * Throws when the poll was cancelled after the handler last checked {@link #isCancelled()}: the
   * probes never reach the worker, and the handler yields them only when delivery fails (D41).
   */
  @Override
  public void onCompleted() {
    if (!result.complete(
        ResponseEntity.ok(ProbeActivationResult.Builder.create().probes(probes).build()))) {
      throw new IllegalStateException("the request was cancelled before the probes were sent");
    }
  }

  @Override
  public void onNext(final ProbeBatchRecord batch) {
    // mapped at once: the handler may reuse the batch once this call returns
    probes.addAll(ProbeMapper.toActivatedProbes(batch, physicalTenantId));
  }

  @Override
  public boolean isCancelled() {
    return result.isCancelled() || result.isCompletedExceptionally();
  }

  @Override
  public void onError(final Throwable throwable) {
    result.complete(RestErrorMapper.mapErrorToResponse(ErrorMapper.mapError(throwable)));
  }

  void setCancelationHandler(final Runnable handler) {
    cancelationHandler = handler;
  }

  void invokeCancelationHandler() {
    final var handler = cancelationHandler;
    if (handler != null) {
      handler.run();
    }
  }
}
