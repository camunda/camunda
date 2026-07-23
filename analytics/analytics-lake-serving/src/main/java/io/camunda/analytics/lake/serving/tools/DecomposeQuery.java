/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.Map;

/**
 * {@code POST /api/tools/decompose} request.
 *
 * <p>{@code filters} is one field beyond the API contract's documented shape ({@code entity,
 * measure/quantile, window, baseline, dim}): an optional, backward-compatible addition (defaults to
 * no filtering when omitted) so {@code POST /api/investigate} can decompose within the same
 * filtered scope its own {@code from}/{@code to}/{@code filters} describe, rather than losing the
 * caller's filters the moment it delegates to this tool. See the module README/report for why.
 */
public record DecomposeQuery(
    String entity,
    String measure,
    Double quantile,
    Map<String, Object> filters,
    TimeRange window,
    TimeRange baseline,
    String dim) {}
