/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.camunda.zeebe.util.CloseableSilently;

public class NoopColumnFamilyMetrics implements ColumnFamilyMetrics {

  @Override
  public CloseableSilently measureGetLatency() {
    return () -> {};
  }

  @Override
  public CloseableSilently measurePutLatency() {
    return () -> {};
  }

  @Override
  public CloseableSilently measureDeleteLatency() {
    return () -> {};
  }

  @Override
  public CloseableSilently measureIterateLatency() {
    return () -> {};
  }
}
