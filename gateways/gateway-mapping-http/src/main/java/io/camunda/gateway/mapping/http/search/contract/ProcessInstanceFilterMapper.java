/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceFilterStrictContract;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Delegates to {@link SearchQueryFilterMapper#toProcessInstanceFilter(
 * GeneratedProcessInstanceFilterStrictContract)} — this facade exists so that
 * SearchQueryRequestMapper can call all strict-contract filter mappers through individual mapper
 * classes, enabling SearchQueryFilterMapper strict-contract overloads to be removed in a later
 * cleanup step.
 */
@NullMarked
public final class ProcessInstanceFilterMapper {

  private ProcessInstanceFilterMapper() {}

  public static Either<List<String>, ProcessInstanceFilter> toProcessInstanceFilter(
      @Nullable final GeneratedProcessInstanceFilterStrictContract filter) {
    return SearchQueryFilterMapper.toProcessInstanceFilter(filter);
  }
}
