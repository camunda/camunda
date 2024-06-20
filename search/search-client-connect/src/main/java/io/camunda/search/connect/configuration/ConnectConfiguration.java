/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.search.connect.SearchClientConnectException;
import java.net.URI;
import java.net.URISyntaxException;

public class ConnectConfiguration {

  private static final String DATABASE_TYPE_DEFAULT = "elasticsearch";
  private static final String CLUSTER_NAME_DEFAULT = "elasticsearch";
  private static final String DATE_FORMAT_FIELD = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private static final String FIELD_DATE_FORMAT_DEFAULT = "date_time";
  private static final String URL_DEFAULT = "http://localhost:9200";

  private String type = DATABASE_TYPE_DEFAULT;
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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getFieldDateFormat() {
    return fieldDateFormat;
  }

  public void setFieldDateFormat(String fieldDateFormat) {
    this.fieldDateFormat = fieldDateFormat;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Integer connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public SecurityConfiguration getSecurity() {
    return security;
  }

  public void setSecurity(SecurityConfiguration security) {
    this.security = security;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @JsonIgnore
  public boolean hasBasicAuthenticationConfigured() {
    final var username = getUsername();
    final var password = getPassword();
    return !(username == null || password == null || username.isEmpty() || password.isEmpty());
  }

  public URI getUrlAsUri() {
    try {
      return new URI(getUrl());
    } catch (URISyntaxException e) {
      throw new SearchClientConnectException("Error in url: " + getUrl(), e);
    }
  }
}
