/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClusterMetadata implements Serializable {

  private String uuid;
  private String name;
  private Map<AppName, String> urls = new HashMap<>();

  public String getUuid() {
    return uuid;
  }

  public ClusterMetadata setUuid(final String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getName() {
    return name;
  }

  public ClusterMetadata setName(final String name) {
    this.name = name;
    return this;
  }

  public Map<AppName, String> getUrls() {
    return urls;
  }

  public ClusterMetadata setUrls(final Map<AppName, String> urls) {
    this.urls = urls;
    return this;
  }

  public List<C8AppLink> getUrlsAsC8AppLinks() {
    return urls.keySet().stream()
        .map(name -> new C8AppLink(name.name(), urls.get(name)))
        .collect(Collectors.toList());
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, name, urls);
  }

  @Override
  public boolean equals(final Object o) {
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
    return String.format("ClusterMetadata{uuid='%s', name='%s', urls='%s'}", uuid, name, urls);
  }

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public record C8AppLink(@JsonProperty("name") String name, @JsonProperty("link") String link) {}

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

    public String getValue() {
      return name().toLowerCase();
    }

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
  }
}
