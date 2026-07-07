/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

/**
 * A user-declared dataset failed validation — the declaration itself is malformed (bad meter
 * params, unresolvable meter type, invalid tiers or names), never a fault of the engine. Thrown at
 * compile/admission time so a bad declaration is rejected at the API instead of surfacing as a raw
 * parse exception during a live topology reload; the message always carries the dataset (and, where
 * applicable, meter/param) context.
 *
 * <p>Extends {@link IllegalArgumentException} because a rejected declaration <em>is</em> an illegal
 * argument: every existing catch site and HTTP error mapping that treats {@code
 * IllegalArgumentException} as a client error handles this one identically.
 */
public class DatasetValidationException extends IllegalArgumentException {

  public DatasetValidationException(final String message) {
    super(message);
  }

  public DatasetValidationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
