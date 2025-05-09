/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.response.DecisionInstance;

/** A selector for decisions. */
@FunctionalInterface
public interface DecisionSelector {
  /**
   * Checks if the decision instance matches the selector.
   *
   * @param instance the decision instance
   * @return {@code true} if the decision matches, otherwise {@code false}
   */
  boolean test(DecisionInstance instance);

  /**
   * Returns a string representation of the selector. It is used to build the failure message of an
   * assertion. Default: {@link Object#toString()}.
   *
   * @return the string representation
   */
  default String describe() {
    return toString();
  }

  /**
   * Applies the given filter to limit the search of decision instances that match the selector.
   * Default: no filtering.
   *
   * @param filter the filter used to limit the decision instance search
   */
  default void applyFilter(final DecisionInstanceFilter filter) {}
}
