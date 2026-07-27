/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

/**
 * Typed error categories for {@link SecretResolutionResult.Failed}.
 *
 * <p>New codes may be added in future minor versions as additional store implementations are
 * introduced. Callers performing exhaustive switches should include a {@code default} branch to
 * handle unrecognized codes gracefully.
 */
public enum SecretErrorCode {
  NOT_FOUND,
  ACCESS_DENIED,
  INVALID_REF,
  UNREADABLE
}
