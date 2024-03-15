/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.properties.common;

public class Keycloak {

  private String url;
  private String realm;
  private String tokenUrl;

  @Override
  public String toString() {
    return "Keycloak{"
        + "url='"
        + url
        + '\''
        + ", realm='"
        + realm
        + '\''
        + ", tokenUrl='"
        + tokenUrl
        + '\''
        + '}';
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getRealm() {
    return realm;
  }

  public void setRealm(final String realm) {
    this.realm = realm;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(final String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }
}
