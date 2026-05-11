/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.ArrayList;
import java.util.List;

public class PhysicalTenantConfiguration {

  private String id;
  private String name;
  private List<String> idps = new ArrayList<>();
  private PhysicalTenantSecurity security = new PhysicalTenantSecurity();

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public List<String> getIdps() {
    return idps;
  }

  public void setIdps(final List<String> idps) {
    this.idps = idps == null ? new ArrayList<>() : idps;
  }

  public PhysicalTenantSecurity getSecurity() {
    return security;
  }

  public void setSecurity(final PhysicalTenantSecurity security) {
    this.security = security;
  }

  public static class PhysicalTenantSecurity {

    private InitializationConfiguration initialization = new InitializationConfiguration();

    public InitializationConfiguration getInitialization() {
      return initialization;
    }

    public void setInitialization(final InitializationConfiguration initialization) {
      this.initialization = initialization;
    }
  }
}
