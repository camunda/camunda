/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * The lifecycle transition a {@link Fact} records. Carried as the reserved {@link Fact#TRANSITION}
 * field (by name, so it can also be a group-by dimension) and read by the composite
 * lifecycle-summary meter to count activations/completions/terminations separately from the same
 * fold.
 */
public enum Transition {
  ACTIVATED,
  COMPLETED,
  TERMINATED,
  CREATED,
  RESOLVED,
  DEPLOYED
}
