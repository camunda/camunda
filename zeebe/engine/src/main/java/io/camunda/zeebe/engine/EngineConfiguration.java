/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import java.time.Duration;

public final class EngineConfiguration {

  public static final int DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;
  public static final Duration DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL = Duration.ofMinutes(1);

  public static final int DEFAULT_MAX_ERROR_MESSAGE_SIZE = 10000;

  // This size (in bytes) is used as a buffer when filling an event/command up to the maximum
  // message size.
  public static final int BATCH_SIZE_CALCULATION_BUFFER = 1024 * 8;

  public static final int DEFAULT_DRG_CACHE_CAPACITY = 1000;

  private int messagesTtlCheckerBatchLimit = DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
  private Duration messagesTtlCheckerInterval = DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;
  private int drgCacheCapacity = DEFAULT_DRG_CACHE_CAPACITY;

  public int getMessagesTtlCheckerBatchLimit() {
    return messagesTtlCheckerBatchLimit;
  }

  public EngineConfiguration setMessagesTtlCheckerBatchLimit(
      final int messagesTtlCheckerBatchLimit) {
    this.messagesTtlCheckerBatchLimit = messagesTtlCheckerBatchLimit;
    return this;
  }

  public Duration getMessagesTtlCheckerInterval() {
    return messagesTtlCheckerInterval;
  }

  public EngineConfiguration setMessagesTtlCheckerInterval(
      final Duration messagesTtlCheckerInterval) {
    this.messagesTtlCheckerInterval = messagesTtlCheckerInterval;
    return this;
  }

  public int getDrgCacheCapacity() {
    return drgCacheCapacity;
  }

  public EngineConfiguration setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
    return this;
  }
}
