/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

/**
 * Be aware that all configuration which are part of this class are experimental, which means they
 * are subject to change and to drop. It might be that also some of them are actually dangerous so
 * be aware when you change one of these!
 */
public class ExperimentalCfg {

  private static final boolean DEFAULT_DETECT_REPROCESSING_INCONSISTENCY = false;

  private boolean detectReprocessingInconsistency = DEFAULT_DETECT_REPROCESSING_INCONSISTENCY;

  public boolean isDetectReprocessingInconsistency() {
    return detectReprocessingInconsistency;
  }

  public void setDetectReprocessingInconsistency(final boolean detectReprocessingInconsistency) {
    this.detectReprocessingInconsistency = detectReprocessingInconsistency;
  }

  @Override
  public String toString() {
    return "ExperimentalCfg{"
        + ", detectReprocessingInconsistency="
        + detectReprocessingInconsistency
        + '}';
  }
}
