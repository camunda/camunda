/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.model;

/** Standardized primary validation error codes (skeleton). */
public enum ValidationErrorCode {
  MISSING_REQUIRED,
  EXTRA_PROPERTY,
  ENUM_MISMATCH,
  TYPE_MISMATCH,
  PATTERN_MISMATCH,
  AMBIGUOUS,
  NO_MATCH,
  INTERNAL_ERROR,
  GROUP_NOT_FOUND
}
