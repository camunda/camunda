/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.agentic;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs current and previous period queries in parallel via CompletableFuture. Used only by
 * getSummary for period-delta computation.
 */
@Component
@NullMarked
public class PeriodComparisonExecutor {

  private final Executor executor;

  public PeriodComparisonExecutor(@Qualifier("agenticQueryExecutor") final Executor executor) {
    this.executor = executor;
  }

  public <T> PeriodComparisonResult<T> execute(
      final AgentQueryParams params, final Function<AgentQueryParams, T> query) {
    final CompletableFuture<T> current =
        CompletableFuture.supplyAsync(() -> query.apply(params), executor);
    final CompletableFuture<T> previous =
        CompletableFuture.supplyAsync(
            () -> query.apply(params.forPreviousPeriodWindow()), executor);
    return new PeriodComparisonResult<>(current.join(), previous.join());
  }
}
