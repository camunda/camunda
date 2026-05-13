/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import java.util.ArrayList;
import java.util.List;

public class WebappConfiguration {

  private boolean enterprise = false;
  private boolean loginDelegated = false;
  private Cloud cloud = new Cloud();
  private List<String> activeComponents = new ArrayList<>();

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public boolean isLoginDelegated() {
    return loginDelegated;
  }

  public void setLoginDelegated(final boolean loginDelegated) {
    this.loginDelegated = loginDelegated;
  }

  public Cloud getCloud() {
    return cloud;
  }

  public void setCloud(final Cloud cloud) {
    this.cloud = cloud;
  }

  public List<String> getActiveComponents() {
    return activeComponents;
  }

  public void setActiveComponents(final List<String> activeComponents) {
    this.activeComponents = activeComponents;
  }

  public static class Cloud {
    private String stage;
    private String mixpanelToken;
    private String mixpanelApiHost;

    public String getStage() {
      return stage;
    }

    public void setStage(final String stage) {
      this.stage = stage;
    }

    public String getMixpanelToken() {
      return mixpanelToken;
    }

    public void setMixpanelToken(final String mixpanelToken) {
      this.mixpanelToken = mixpanelToken;
    }

    public String getMixpanelApiHost() {
      return mixpanelApiHost;
    }

    public void setMixpanelApiHost(final String mixpanelApiHost) {
      this.mixpanelApiHost = mixpanelApiHost;
    }
  }
}
