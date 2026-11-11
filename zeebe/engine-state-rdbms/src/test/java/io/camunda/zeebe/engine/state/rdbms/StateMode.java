/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assumptions.assumeThat;

/** How a test's {@link EntitySession} writes the rows of the relational engine state. */
enum StateMode {
  /** Every commit writes its rows to the database. */
  SYNCHRONOUS,
  /** A commit hands its rows to a write-behind store, which writes them to the database later. */
  WRITE_BEHIND;

  /** Skips a test that asserts the statements or connections of a commit that writes at once. */
  void assumeSynchronous(final String reason) {
    assumeThat(this == SYNCHRONOUS).describedAs(reason).isTrue();
  }
}
