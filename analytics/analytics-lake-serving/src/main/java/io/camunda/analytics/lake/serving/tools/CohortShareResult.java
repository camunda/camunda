/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.List;

/**
 * {@code POST /api/tools/cohort-share} response: one {@link ShareSeries} per requested threshold,
 * plus every SQL statement executed to produce them.
 */
public record CohortShareResult(List<ShareSeries> series, List<String> sql) {}
