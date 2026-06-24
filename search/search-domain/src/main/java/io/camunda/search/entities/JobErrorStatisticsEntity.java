/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import org.jspecify.annotations.Nullable;

/**
 * Represents aggregated job error statistics for a specific error type and message combination.
 *
 * @param errorCode the error code identifier
 * @param errorMessage the error message
 * @param workers the number of distinct workers that encountered this error
 */
public record JobErrorStatisticsEntity(
    // composite aggregation bucket key; null when the source doc omits the field.
    @Nullable String errorCode,
    // composite aggregation bucket key; null when the source doc omits the field.
    @Nullable String errorMessage,
    int workers) {}
