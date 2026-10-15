/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The SCIM attributes that are stored on users and groups, as configured by the operator under
 * {@code camunda.scim.user-attributes} and {@code camunda.scim.group-attributes}. Nothing is stored
 * by default.
 */
@ConfigurationProperties(prefix = "camunda.scim")
public class ScimAttributeProperties {

  private List<Attribute> userAttributes = new ArrayList<>();
  private List<Attribute> groupAttributes = new ArrayList<>();
  private List<SourceProperties> sources = new ArrayList<>();

  public List<SourceProperties> getSources() {
    return sources;
  }

  public void setSources(final List<SourceProperties> sources) {
    this.sources = sources;
  }

  public List<Attribute> getUserAttributes() {
    return userAttributes;
  }

  public void setUserAttributes(final List<Attribute> userAttributes) {
    this.userAttributes = userAttributes;
  }

  public List<Attribute> getGroupAttributes() {
    return groupAttributes;
  }

  public void setGroupAttributes(final List<Attribute> groupAttributes) {
    this.groupAttributes = groupAttributes;
  }

  /** A stored attribute: the key it is stored under and the SCIM attribute path it is read from. */
  public static class Attribute {
    private String name;
    private String path;

    public Attribute() {}

    public Attribute(final String name, final String path) {
      this.name = name;
      this.path = path;
    }

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }
  }

  /** A SCIM source as configured under {@code camunda.scim.sources}. */
  public static class SourceProperties {
    private String id;
    private List<String> clientIds = new ArrayList<>();
    private String oidcRegistration;
    private String loginBindingClaim;
    private boolean adoptUnowned;

    public String getId() {
      return id;
    }

    public void setId(final String id) {
      this.id = id;
    }

    public List<String> getClientIds() {
      return clientIds;
    }

    public void setClientIds(final List<String> clientIds) {
      this.clientIds = clientIds;
    }

    public String getOidcRegistration() {
      return oidcRegistration;
    }

    public void setOidcRegistration(final String oidcRegistration) {
      this.oidcRegistration = oidcRegistration;
    }

    public String getLoginBindingClaim() {
      return loginBindingClaim;
    }

    public void setLoginBindingClaim(final String loginBindingClaim) {
      this.loginBindingClaim = loginBindingClaim;
    }

    public boolean isAdoptUnowned() {
      return adoptUnowned;
    }

    public void setAdoptUnowned(final boolean adoptUnowned) {
      this.adoptUnowned = adoptUnowned;
    }
  }
}
