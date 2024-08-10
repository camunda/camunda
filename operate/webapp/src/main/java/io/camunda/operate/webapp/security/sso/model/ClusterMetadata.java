/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.*;

public class ClusterMetadata implements Serializable {

  private String uuid;
  private String name;
  private Map<AppName, String> urls = new HashMap<>();

  public String getUuid() {
    return uuid;
  }

  public ClusterMetadata setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getName() {
    return name;
  }

  public ClusterMetadata setName(String name) {
    this.name = name;
    return this;
  }

  public Map<AppName, String> getUrls() {
    return urls;
  }

  public ClusterMetadata setUrls(Map<AppName, String> urls) {
    this.urls = urls;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, name, urls);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterMetadata that = (ClusterMetadata) o;
    return Objects.equals(uuid, that.uuid)
        && Objects.equals(name, that.name)
        && Objects.equals(urls, that.urls);
  }

  @Override
  public String toString() {
    return "ClusterMetadata{"
        + "uuid='"
        + uuid
        + '\''
        + ", name='"
        + name
        + '\''
        + ", urls="
        + urls
        + '\''
        + '}';
  }

  public enum AppName {
    @JsonProperty("console")
    CONSOLE,
    @JsonProperty("operate")
    OPERATE,
    @JsonProperty("optimize")
    OPTIMIZE,
    @JsonProperty("modeler")
    MODELER,
    @JsonProperty("tasklist")
    TASKLIST,
    @JsonProperty("zeebe")
    ZEEBE,
    @JsonProperty("connectors")
    CONNECTORS;

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
  }
}
