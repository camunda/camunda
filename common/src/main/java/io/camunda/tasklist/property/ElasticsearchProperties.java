/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.property;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.net.URI;
import java.net.URISyntaxException;

public class ElasticsearchProperties {

  public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

  public static final String ELS_DATE_FORMAT_DEFAULT = "date_time";

  private String clusterName = "elasticsearch";

  private String host = "localhost";

  private int port = 9200;

  private String url;
  private String username;
  private String password;

  private String dateFormat = DATE_FORMAT_DEFAULT;

  private String elsDateFormat = ELS_DATE_FORMAT_DEFAULT;

  private int batchSize = 2000;

  private boolean createSchema = true;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getElsDateFormat() {
    return elsDateFormat;
  }

  public void setElsDateFormat(String elsDateFormat) {
    this.elsDateFormat = elsDateFormat;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(boolean createSchema) {
    this.createSchema = createSchema;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
    setHost(getURI().getHost());
    setPort(getURI().getPort());
  }

  public URI getURI() {
    if (url == null) {
      try {
        return new URI(String.format("http://%s:%d", getHost(), getPort()));
      } catch (URISyntaxException e) {
        throw new TasklistRuntimeException(
            String.format(
                "Failed to build URI from host %s and port %d url ", getHost(), getPort()),
            e);
      }
    } else {
      try {
        return new URI(url);
      } catch (final URISyntaxException e) {
        throw new TasklistRuntimeException("Failed to parse url " + url, e);
      }
    }
  }
}
