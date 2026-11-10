/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import org.apache.ibatis.session.SqlSession;

/**
 * A hook that is called at the start of each database transaction, before any queued items are
 * executed. Implementations can use the provided {@link SqlSession} to execute additional
 * statements within the same transaction, such as acquiring row-level locks via {@code SELECT FOR
 * UPDATE} or validating state.
 */
@FunctionalInterface
public interface InTransactionHook {

  /**
   * Called at the beginning of a database transaction, before any queued items are executed.
   *
   * @param session the active database session for the current transaction
   */
  void onTransactionStart(SqlSession session);
}
