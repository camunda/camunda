/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

import java.util.Objects;

public class CloudProperties {

  private String permissionUrl;
  private String permissionAudience;

  private String clusterId;

  private String consoleUrl;

  public String getPermissionUrl() {
    return permissionUrl;
  }

  public void setPermissionUrl(final String permissionUrl) {
    this.permissionUrl = permissionUrl;
  }

  public String getPermissionAudience() {
    return permissionAudience;
  }

  public void setPermissionAudience(final String permissionAudience) {
    this.permissionAudience = permissionAudience;
  }

  public String getClusterId() {
    return clusterId;
  }

  public CloudProperties setClusterId(final String clusterId) {
    this.clusterId = clusterId;
    return this;
  }

  public String getConsoleUrl() {
    return consoleUrl;
  }

  public CloudProperties setConsoleUrl(final String consoleUrl) {
    this.consoleUrl = consoleUrl;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CloudProperties that = (CloudProperties) o;
    return Objects.equals(permissionUrl, that.permissionUrl)
        && Objects.equals(permissionAudience, that.permissionAudience)
        && Objects.equals(clusterId, that.clusterId)
        && Objects.equals(consoleUrl, that.consoleUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(permissionUrl, permissionAudience, clusterId, consoleUrl);
  }
}
