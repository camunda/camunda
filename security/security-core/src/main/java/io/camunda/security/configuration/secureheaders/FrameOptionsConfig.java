/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.FrameOptionMode;

public class FrameOptionsConfig {
  private boolean enabled = true;
  private FrameOptionMode mode = FrameOptionMode.SAMEORIGIN;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean disabled() {
    return !enabled;
  }

  public FrameOptionMode getMode() {
    return mode;
  }

  public void setMode(final FrameOptionMode mode) {
    this.mode = mode;
  }
}
