/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code camunda.webapps} keys the {@code /.well-known/camunda/webapps} discovery
 * endpoint needs. The unified configuration module binds the same keys through its own {@code
 * Camunda} bean; gateway-rest cannot depend on that module (the dependency points the other way),
 * so it binds the subtree it consumes on its own. Keep the fields aligned with {@code
 * io.camunda.configuration.Webapp}.
 */
@ConfigurationProperties(prefix = "camunda.webapps")
public class WebappsDiscoveryProperties {

  private Webapp operate = new Webapp();
  private Webapp tasklist = new Webapp();

  public Webapp getOperate() {
    return operate;
  }

  public void setOperate(final Webapp operate) {
    this.operate = operate;
  }

  public Webapp getTasklist() {
    return tasklist;
  }

  public void setTasklist(final Webapp tasklist) {
    this.tasklist = tasklist;
  }

  public static class Webapp {

    /**
     * External base URL of the webapp UI, announced as-is when set. When unset, the webapp's
     * default path on the API's origin is announced instead, as long as the UI is enabled.
     */
    private @Nullable String url;

    /** Whether the webapp is enabled or not. */
    private boolean enabled = true;

    /** Whether the webapp UI is enabled or not. */
    private boolean uiEnabled = true;

    public @Nullable String getUrl() {
      return url;
    }

    public void setUrl(final @Nullable String url) {
      this.url = url;
    }

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
}
