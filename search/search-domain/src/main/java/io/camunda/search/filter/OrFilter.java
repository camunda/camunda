/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link FilterBase} that additionally supports a top-level {@code $or} clause: a list of
 * alternative filters of the same type, at least one of which must match.
 */
public interface OrFilter<T extends OrFilter<T>> extends FilterBase {

  Logger LOGGER = LoggerFactory.getLogger(OrFilter.class);

  List<T> orFilters();

  /** Whether this filter carries no criteria of its own (ignoring {@link #orFilters()}). */
  @JsonIgnore
  boolean isEmpty();

  // An empty group has no criteria of its own, so it matches every document; ORing anything
  // with "matches everything" is itself "matches everything", so the whole $or clause collapses
  // into a no-op rather than narrowing to the remaining groups. Logged because that collapse can
  // otherwise look like a silently ignored filter to whoever is debugging a search request.
  default boolean hasEmptyOrFilter() {
    final var orFilters = orFilters();
    final var hasEmptyGroup =
        orFilters != null
            && orFilters.stream().anyMatch(filter -> filter == null || filter.isEmpty());
    if (hasEmptyGroup) {
      LOGGER.debug(
          "{} has an empty $or group; the whole $or clause is dropped as a no-op instead of"
              + " narrowing to the remaining group(s)",
          getClass().getSimpleName());
    }
    return hasEmptyGroup;
  }
}
