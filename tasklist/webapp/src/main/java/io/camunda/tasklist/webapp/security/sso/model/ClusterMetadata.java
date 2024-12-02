/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.sso.model;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.tasklist.webapp.dto.C8AppLink;
import java.io.Serializable;
import java.util.*;
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
        .map(name -> new C8AppLink().setName(name.name()).setLink(urls.get(name)))
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
