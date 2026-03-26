/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

public class JobActivationRequestResponseObserver implements ResponseObserver<JobActivationResult> {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobActivationRequestResponseObserver.class);

  protected JobActivationResult response = new JobActivationResult();
  protected CompletableFuture<ResponseEntity<Object>> result;

  private final BiConsumer<List<ActivatedJob>, String> yieldCallback;
  private final List<ActivatedJob> activatedJobs = new ArrayList<>();
  private Runnable cancelationHandler;

  public JobActivationRequestResponseObserver(
      final CompletableFuture<ResponseEntity<Object>> result,
      final ObjectMapper objectMapper,
      final BiConsumer<List<ActivatedJob>, String> yieldCallback) {
    this.result = result;
    this.yieldCallback = yieldCallback;
  }

  @Override
  public void onCompleted() {
    final int jobCount = activatedJobs.size();
    LOG.debug(
        "onCompleted: completing response with {} jobs, future cancelled={}",
        jobCount,
        result.isCancelled());
    result.complete(
        ResponseEntity.ok()
            .body(new YieldOnWriteFailureResult(response, activatedJobs, yieldCallback)));
  }

  @Override
  public void onNext(final JobActivationResult element) {
    if (element.getJobs() != null && !element.getJobs().isEmpty()) {
      LOG.debug(
          "onNext: received {} jobs (keys: {}), future cancelled={}",
          element.getJobs().size(),
          element.getJobs().stream()
              .map(j -> j.getJobKey())
              .collect(java.util.stream.Collectors.joining(",")),
          result.isCancelled());
      element.getJobs().forEach(response::addJobsItem);
      element
          .getJobs()
          .forEach(
              job ->
                  activatedJobs.add(
                      new ActivatedJob(Long.parseLong(job.getJobKey()), job.getRetries())));
    }
  }

  @Override
  public boolean isCancelled() {
    // the CompletableFuture will be canceled eventually if the request is canceled
    final boolean cancelled = result.isCancelled() || result.isCompletedExceptionally();
    if (cancelled) {
      LOG.debug("isCancelled: true (jobs accumulated so far: {})", activatedJobs.size());
    }
    return cancelled;
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

  /**
   * Wraps a {@link JobActivationResult} and intercepts Jackson serialization to detect write
   * failures. When the HTTP response write fails (e.g., client sent RST), the yield callback is
   * invoked to reactivate locked jobs before the exception propagates.
   *
   * <p>This approach is necessary because {@code StreamingResponseBody} does not work inside {@code
   * CompletableFuture<ResponseEntity<Object>>} — Spring's {@code
   * StreamingResponseBodyReturnValueHandler} only matches when the declared generic type is {@code
   * StreamingResponseBody}, not {@code Object}.
   */
  static class YieldOnWriteFailureResult implements JsonSerializable {

    private final JobActivationResult response;
    private final List<ActivatedJob> activatedJobs;
    private final BiConsumer<List<ActivatedJob>, String> yieldCallback;

    YieldOnWriteFailureResult(
        final JobActivationResult response,
        final List<ActivatedJob> activatedJobs,
        final BiConsumer<List<ActivatedJob>, String> yieldCallback) {
      this.response = response;
      this.activatedJobs = activatedJobs;
      this.yieldCallback = yieldCallback;
    }

    @Override
    public void serialize(final JsonGenerator gen, final SerializerProvider serializers)
        throws IOException {
      final int jobCount = response.getJobs() != null ? response.getJobs().size() : 0;
      LOG.debug("serialize: writing response with {} jobs to client", jobCount);

      // Probe the connection before writing: if the client sent RST, reading from the
      // request input stream throws IOException immediately (unlike writes which may
      // succeed into the kernel send buffer). This detects stale long-poll connections
      // where the TCP connection is dead but the server-side request object is still queued.
      if (!activatedJobs.isEmpty()) {
        probeConnection(jobCount);
      }

      try {
        serializers.defaultSerializeValue(response, gen);
        gen.flush();
        LOG.debug("serialize: successfully wrote {} jobs to client", jobCount);
      } catch (final IOException e) {
        LOG.warn(
            "serialize: WRITE FAILED for {} jobs, yielding {} activated jobs",
            jobCount,
            activatedJobs.size(),
            e);
        if (!activatedJobs.isEmpty()) {
          yieldCallback.accept(activatedJobs, "Client disconnected during response write");
        }
        throw e;
      }
    }

    private void probeConnection(final int jobCount) throws IOException {
      try {
        final var requestAttributes =
            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (requestAttributes
            instanceof
            org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
          final var response = servletAttrs.getResponse();
          if (response != null) {
            // Force Tomcat to commit the HTTP response (send status line + headers) to
            // the socket. On a healthy connection this succeeds. On a RST'd connection,
            // the socket write fails with IOException, allowing us to detect the dead
            // connection before serializing the job activation response.
            response.flushBuffer();
            LOG.debug(
                "serialize: connection probe succeeded for {} jobs (connection alive)", jobCount);
          }
        }
      } catch (final IOException e) {
        LOG.warn(
            "serialize: connection probe FAILED for {} jobs, yielding {} activated jobs",
            jobCount,
            activatedJobs.size(),
            e);
        yieldCallback.accept(activatedJobs, "Client disconnected (detected via flush probe)");
        throw e;
      }
    }

    @Override
    public void serializeWithType(
        final JsonGenerator gen, final SerializerProvider serializers, final TypeSerializer typeSer)
        throws IOException {
      serialize(gen, serializers);
    }
  }
}
