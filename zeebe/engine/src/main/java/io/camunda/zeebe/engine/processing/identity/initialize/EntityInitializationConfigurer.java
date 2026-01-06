/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.List;

public interface EntityInitializationConfigurer<T, U extends UnifiedRecordValue> {

  default Either<List<String>, List<U>> configureEntities(final List<T> auths) {
    return auths.stream()
        .map(this::configure)
        .collect(Either.collector())
        .mapLeft(violations -> violations.stream().flatMap(List::stream).toList());
  }

  /**
   * Validate and map a configured entity for initialization.
   *
   * @return Either the configured entity or a list of validation violations.
   */
  Either<List<String>, U> configure(T entity);
}
