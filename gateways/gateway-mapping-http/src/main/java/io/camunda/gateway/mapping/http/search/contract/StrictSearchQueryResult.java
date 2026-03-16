/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Generic Jackson-serializable search query result record.
 *
 * <p>Replaces the protocol model's type-specific search query result wrappers (e.g. {@code
 * ProcessDefinitionSearchQueryResult}) in the strict contract response path. Jackson serializes
 * this to the same {@code {"items": [...], "page": {...}}} JSON shape.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
public record StrictSearchQueryResult<T>(
    @Nullable List<T> items, @Nullable StrictSearchQueryPage page) {}
