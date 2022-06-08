/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class Auth0Properties {

  public static final String DEFAULT_ROLES_KEY = "https://camunda.com/roles";

  /**
   * Defines the domain which the user always sees<br>
   * auth0.com call it <b>Custom Domain</b>
   */
  private String domain;

  /**
   * Defines the domain which provides information about the user<br>
   * auth0.com call it <b>Domain</b>
   */
  private String backendDomain;

  /**
   * This is the client id of auth0 application (see Settings page on auth0 dashboard) It's like an
   * user name for the application
   */
  private String clientId;

  /**
   * This is the client secret of auth0 application (see Settings page on auth0 dashboard) It's like
   * a password for the application
   */
  private String clientSecret;

  /** The claim we want to check It's like a permission name */
  private String claimName;

  /** The given organization should be contained in value of claim key (claimName) - MUST given */
  private String organization;

  /** Key for claim to retrieve the user name */
  private String nameKey = "name";

  /** Key for claim to retrieve the user email */
  private String emailKey = "email";

  private String rolesKey = DEFAULT_ROLES_KEY;

  public String getRolesKey() {
    return rolesKey;
  }

  public void setRolesKey(final String rolesKey) {
    this.rolesKey = rolesKey;
  }

  public String getDomain() {
    return domain;
  }

  public Auth0Properties setDomain(final String domain) {
    this.domain = domain;
    return this;
  }

  public String getBackendDomain() {
    return backendDomain;
  }

  public Auth0Properties setBackendDomain(final String backendDomain) {
    this.backendDomain = backendDomain;
    return this;
  }

  public String getClientId() {
    return clientId;
  }

  public Auth0Properties setClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public Auth0Properties setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  public String getClaimName() {
    return claimName;
  }

  public Auth0Properties setClaimName(final String claimName) {
    this.claimName = claimName;
    return this;
  }

  public String getOrganization() {
    return organization;
  }

  public Auth0Properties setOrganization(final String organization) {
    this.organization = organization;
    return this;
  }

  public String getNameKey() {
    return nameKey;
  }

  public Auth0Properties setNameKey(final String nameKey) {
    this.nameKey = nameKey;
    return this;
  }

  public String getEmailKey() {
    return emailKey;
  }

  public Auth0Properties setEmailKey(final String emailKey) {
    this.emailKey = emailKey;
    return this;
  }
}
