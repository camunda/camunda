/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.jspecify.annotations.Nullable;

public class Webapp {

  /** Whether the webapp is enabled or not. This also affects the webapp API. */
  private boolean enabled = true;

  /**
   * Whether the webapp UI is enabled or not. If false, the webapp API will still be available, but
   * the webapp itself will not be accessible with a web browser.
   */
  private boolean uiEnabled = true;

  /**
   * The external base URL of the webapp UI (e.g. {@code https://operate.example.com}), announced to
   * clients via the unauthenticated {@code /.well-known/camunda/webapps} discovery endpoint.
   *
   * <p>Set this when the webapp is reachable under a different origin or path than the
   * orchestration cluster REST API, e.g. behind a dedicated ingress. If unset and the webapp UI is
   * enabled, the discovery endpoint announces the webapp's default path relative to the API (e.g.
   * {@code /operate}). If the webapp UI is disabled and no URL is set, the webapp is not announced.
   *
   * <p>Currently only Operate and Tasklist are announced by the discovery endpoint; setting a URL
   * for other webapps has no effect.
   */
  private @Nullable String url;

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

  public @Nullable String getUrl() {
    return url;
  }

  public void setUrl(final @Nullable String url) {
    this.url = url;
  }
}
