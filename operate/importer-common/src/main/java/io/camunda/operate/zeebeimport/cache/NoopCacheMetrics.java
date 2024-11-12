/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

public class NoopCacheMetrics implements TreePathCacheMetrics {

  @Override
  public void reportCacheResult(final int partitionId, final CacheResult result) {}

  @Override
  public void reportCacheSize(final int partitionId, final int size) {}
}
