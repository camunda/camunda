/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

/**
 * Central definition of the Business ID length constraint, shared by the process-instance creation
 * path (which turns a violation into a command rejection), the call-activity resolution path (which
 * turns it into a runtime incident), and the deployment validation path (which rejects a static
 * literal that violates the rule at deploy time). Callers adapt the outcome to their own result
 * type; this class owns only the rule.
 */
public final class BusinessIdValidator {

  /** The maximum allowed length of a Business ID. */
  public static final int MAX_BUSINESS_ID_LENGTH = 256;

  private BusinessIdValidator() {}

  /**
   * @param businessId the Business ID to check, may be {@code null}
   * @return {@code true} if the Business ID is non-null and longer than {@link
   *     #MAX_BUSINESS_ID_LENGTH}
   */
  public static boolean exceedsMaxLength(final String businessId) {
    return businessId != null && businessId.length() > MAX_BUSINESS_ID_LENGTH;
  }
}
