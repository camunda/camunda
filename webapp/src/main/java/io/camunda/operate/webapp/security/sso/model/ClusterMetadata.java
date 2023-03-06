/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.*;

public class ClusterMetadata implements Serializable {

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

      @Override public String toString() {
        return super.toString().toLowerCase();
      }
    }

    private String uuid;
    private String name;
    private Map<AppName,String> urls = new HashMap<>();

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

    @Override public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      ClusterMetadata that = (ClusterMetadata) o;
      return Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name) && Objects.equals(urls, that.urls);
    }

    @Override public int hashCode() {
      return Objects.hash(uuid, name, urls);
    }

    @Override public String toString() {
      return "ClusterMetadata{" + "uuid='" + uuid + '\'' + ", name='" + name + '\'' + ", urls=" + urls + '\'' + '}';
    }
}
