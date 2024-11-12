/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class AbstractInterpreterFacade<CRITERION, INTERPRETER> {
  protected final Map<CRITERION, INTERPRETER> interpretersMap;

  public AbstractInterpreterFacade(
      final List<INTERPRETER> interpreters,
      final Function<INTERPRETER, Set<CRITERION>> criteriaExtractor) {
    interpretersMap =
        interpreters.stream()
            .flatMap(
                interpreter ->
                    (criteriaExtractor.apply(interpreter).stream()
                        .map(criterion -> ImmutablePair.of(criterion, interpreter))))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  protected INTERPRETER interpreter(final CRITERION criterion) {
    return Optional.ofNullable(interpretersMap.get(criterion))
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    format(
                        "No interpreter registered for %s in %s",
                        criterion, getClass().getName())));
  }
}
