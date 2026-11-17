/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.validation;

/** Holds validation limits that are fixed and cannot be configured. */
public final class ValidationConstraints {

  /**
   * The maximum allowed msgpack container nesting depth, enforced by {@link NestingDepthValidator}.
   */
  public static final int MAX_NESTING_DEPTH = 1000;

  private ValidationConstraints() {}
}
