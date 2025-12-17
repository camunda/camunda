/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import java.time.Duration;

/**
 * This service is for deleting history on user request. For data retention see {@link
 * HistoryCleanupService}.
 */
public class HistoryDeletionService {

  public HistoryDeletionService() {
    // TODO add history deletion reader to constructor
    // TODO add history deletion writer to constructor

  }

  public Duration deleteHistory(final int partitionId) {
    System.out.println("DELETE HISTORY FOR PARTITION " + partitionId);
    return Duration.ofSeconds(1);
  }
}
