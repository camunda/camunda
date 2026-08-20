/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Webapp {

  /** Whether the webapp is enabled or not. This also affects the webapp API. */
  private boolean enabled = true;

  /**
   * Whether the webapp UI is enabled or not. If false, the webapp API will still be available, but
   * the webapp itself will not be accessible with a web browser.
   */
  private boolean uiEnabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isUiEnabled() {
    return uiEnabled;
  }

  public void setUiEnabled(final boolean uiEnabled) {
    this.uiEnabled = uiEnabled;
  }
}
