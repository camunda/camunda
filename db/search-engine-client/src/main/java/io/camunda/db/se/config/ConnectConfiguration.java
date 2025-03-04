/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.se.config;

import java.util.ArrayList;
import java.util.List;

public class ConnectConfiguration {

  private static final DatabaseType DATABASE_TYPE_DEFAULT = DatabaseType.ELASTICSEARCH;
  private static final String CLUSTER_NAME_DEFAULT = "elasticsearch";
  private static final String DATE_FORMAT_FIELD = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private static final String FIELD_DATE_FORMAT_DEFAULT = "date_time";
  private static final String URL_DEFAULT = "http://localhost:9200";
  private String type = DATABASE_TYPE_DEFAULT.toString();
  private String clusterName = CLUSTER_NAME_DEFAULT;
  private String dateFormat = DATE_FORMAT_FIELD;
  private String fieldDateFormat = FIELD_DATE_FORMAT_DEFAULT;
  private Integer socketTimeout;
  private Integer connectTimeout;
  private String url = URL_DEFAULT;
  private String username;
  private String password;
  private SecurityConfiguration security = new SecurityConfiguration();
  private String indexPrefix;
  private List<PluginConfiguration> interceptorPlugins = new ArrayList<>();

  /** Use {@link ConnectConfiguration#getTypeEnum()} */
  @Deprecated
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public DatabaseType getTypeEnum() {
    return DatabaseType.from(type);
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(final String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getFieldDateFormat() {
    return fieldDateFormat;
  }

  public void setFieldDateFormat(final String fieldDateFormat) {
    this.fieldDateFormat = fieldDateFormat;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(final Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(final Integer connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public SecurityConfiguration getSecurity() {
    return security;
  }

  public void setSecurity(final SecurityConfiguration security) {
    this.security = security;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public List<PluginConfiguration> getInterceptorPlugins() {
    return interceptorPlugins;
  }

  public void setInterceptorPlugins(final List<PluginConfiguration> interceptorPlugins) {
    this.interceptorPlugins = interceptorPlugins;
  }
}
