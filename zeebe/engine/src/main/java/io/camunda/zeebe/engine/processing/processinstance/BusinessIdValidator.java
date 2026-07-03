/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.util.Either;

/**
 * Central definition of the Business ID validation rules, shared by the process-instance creation
 * path (which turns a violation into a command rejection), the call-activity resolution path (which
 * turns it into a runtime incident), and the deployment validation path (which rejects a static
 * literal that violates the rule at deploy time). Callers adapt the outcome to their own result
 * type; this class owns the rules and the reason describing a violation, so a future rule change
 * only has to happen here.
 */
public final class BusinessIdValidator {

  /** The maximum allowed length of a Business ID. */
  public static final int MAX_BUSINESS_ID_LENGTH = 256;

  private BusinessIdValidator() {}

  /**
   * Validates the given Business ID against all Business ID rules.
   *
   * @param businessId the Business ID to validate, may be {@code null}
   * @return a {@link Either#left(Object) left} with the reason the Business ID is invalid, or a
   *     {@link Either#right(Object) right} with the validated Business ID
   */
  public static Either<String, String> validate(final String businessId) {
    if (exceedsMaxLength(businessId)) {
      return Either.left("exceeds the max length of %d".formatted(MAX_BUSINESS_ID_LENGTH));
    }
    return Either.right(businessId);
  }

  private static boolean exceedsMaxLength(final String businessId) {
    return businessId != null && businessId.length() > MAX_BUSINESS_ID_LENGTH;
  }
}
