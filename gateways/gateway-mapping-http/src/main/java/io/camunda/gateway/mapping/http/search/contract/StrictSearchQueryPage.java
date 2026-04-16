/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Jackson-serializable pagination record for strict contract search results.
 *
 * <p>Serializes to the same JSON shape as the protocol model's {@code SearchQueryPageResponse}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
public record StrictSearchQueryPage(
    @Nullable Long totalItems,
    @Nullable Boolean hasMoreTotalItems,
    @Nullable String startCursor,
    @Nullable String endCursor) {}
