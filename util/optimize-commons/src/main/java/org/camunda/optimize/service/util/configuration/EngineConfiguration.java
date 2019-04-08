/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

public class EngineConfiguration {

  private String name;
  private String rest;
  private EngineWebappsConfiguration webapps;
  private boolean importEnabled = true;

  private EngineAuthenticationConfiguration authentication;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRest() {
    return rest;
  }

  public void setRest(String rest) {
    rest = removeTrailingSlashes(rest);
    this.rest = rest;
  }

  private String removeTrailingSlashes(String str) {
    return str.replaceAll("/$", "");
  }

  public EngineAuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public void setAuthentication(EngineAuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  public EngineWebappsConfiguration getWebapps() {
    return webapps;
  }

  public void setWebapps(EngineWebappsConfiguration webapps) {
    this.webapps = webapps;
  }

  public boolean isImportEnabled() {
    return importEnabled;
  }

  public void setImportEnabled(final boolean importEnabled) {
    this.importEnabled = importEnabled;
  }
}
