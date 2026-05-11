/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.write.queue.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringTransactionRunner implements TransactionRunner {

  private final TransactionTemplate transactionTemplate;

  public SpringTransactionRunner(final PlatformTransactionManager transactionManager) {
    transactionTemplate = new TransactionTemplate(transactionManager);
    // Explicitly request READ_COMMITTED since SpringManagedTransactionFactory ignores
    // the isolation level hint passed to SqlSessionFactory.openSession().
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Override
  public <T> T runInTransaction(final Supplier<T> callback) {
    return transactionTemplate.execute(ignored -> callback.get());
  }
}
