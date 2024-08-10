/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.prometheus.client.Histogram.Timer;

public class NoopColumnFamilyMetrics implements ColumnFamilyMetrics {

  @Override
  public Timer measureGetLatency() {
    return null;
  }

  @Override
  public Timer measurePutLatency() {
    return null;
  }

  @Override
  public Timer measureDeleteLatency() {
    return null;
  }

  @Override
  public Timer measureIterateLatency() {
    return null;
  }
}
