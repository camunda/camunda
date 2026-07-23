/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.List;
import java.util.Map;

/**
 * Resolved {@code POST /api/tools/cohort-compare} request — {@code attributes: "auto" | [...]} from
 * the wire is resolved to {@code null} (auto) or an explicit list by the controller before this
 * reaches {@link CohortCompareService}.
 */
public record CohortCompareQuery(
    String entity,
    CohortSpec cohort,
    Map<String, Object> filters,
    String from,
    String to,
    List<String> attributes,
    Integer supportFloor) {}
