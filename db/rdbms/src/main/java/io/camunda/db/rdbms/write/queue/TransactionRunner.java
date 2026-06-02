/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.function.Supplier;

/**
 * This interface is primarily used for testing with Spring. Usually, we do not have a
 * PlatformTransactionManager available and manage transactions manually if needed (for example, in
 * the DefaultExecutionQueue). However, some tests initialize a full Spring context and provide
 * their own transaction management with rollback after each test. To keep those tests as simple as
 * possible, this interface offers an option to run code within the Spring transaction context.
 */
@FunctionalInterface
public interface TransactionRunner {

  /**
   * Runs the given code block inside a transaction if possible.
   *
   * @param callback the code to execute inside a transaction
   * @return the return value of the given callback
   * @param <T> the generic return type
   */
  <T> T runInTransaction(Supplier<T> callback);

  /**
   * A transaction runner that only executes the given callback.
   *
   * @return the no-op transaction runner
   */
  static TransactionRunner noop() {
    return new NoopTransactionRunner();
  }

  /** A transaction runner that only executes the given callback. */
  class NoopTransactionRunner implements TransactionRunner {

    @Override
    public <T> T runInTransaction(final Supplier<T> callback) {
      return callback.get();
    }
  }
}
