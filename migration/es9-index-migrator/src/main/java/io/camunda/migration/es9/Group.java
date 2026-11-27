/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import java.util.Locale;

/** A set of indices that one {@code migrate} run handles. */
public enum Group {
  OC,
  OPTIMIZE;

  /** The name used on the command line and in the termination message. */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
