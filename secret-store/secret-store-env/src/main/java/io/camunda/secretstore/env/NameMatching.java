/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

/** How a secret name is mapped to the environment variable that holds its value. */
public enum NameMatching {
  /** The variable is named exactly {@code <prefix><name>}. */
  EXACT,

  /**
   * The lookup Camunda Connectors' environment secret provider gets from Spring's {@code
   * SystemEnvironmentPropertySource}: {@code <prefix><name>} as-is, then with dots, dashes, and
   * both replaced by underscores, then the same four candidates upper-cased. The first variable
   * that exists wins. Lets a customer move variables over from Connectors without renaming them.
   */
  CONNECTORS_COMPATIBLE
}
