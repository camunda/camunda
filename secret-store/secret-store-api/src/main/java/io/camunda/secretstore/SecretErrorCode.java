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
 * <p>New codes may be added as additional store implementations are introduced. Callers switch over
 * them exhaustively and without a {@code default} branch: every store and every caller ships from
 * this repository in the same release, so a code added here has to be mapped at every caller in the
 * same change, and the compiler is what enforces that.
 */
public enum SecretErrorCode {
  NOT_FOUND,
  ACCESS_DENIED,
  INVALID_REF,
  UNREADABLE
}
