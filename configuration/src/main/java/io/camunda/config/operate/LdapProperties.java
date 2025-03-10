/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class LdapProperties {

  // LDAP properties
  // Also used as url for ActiveDirectory
  private String url;
  // Also used as rootDn for ActiveDirectory
  private String baseDn;
  private String userDnPatterns;
  private String userSearchBase;
  private String managerDn;
  private String managerPassword;
  // Also used for ActiveDirectory search filter
  private String userSearchFilter;

  // Properties for specific LDAP service provided by Active Directory Server
  private String domain;

  private String firstnameAttrName = "givenName";
  private String lastnameAttrName = "sn";
  private String displayNameAttrName = "displayname";
  private String userIdAttrName = "uid";

  public String getBaseDn() {
    return baseDn;
  }

  public void setBaseDn(final String baseDn) {
    this.baseDn = baseDn;
  }

  public String getUserSearchBase() {
    return userSearchBase == null ? "" : userSearchBase;
  }

  public void setUserSearchBase(final String userSearchBase) {
    this.userSearchBase = userSearchBase;
  }

  public String getManagerDn() {
    return managerDn;
  }

  public void setManagerDn(final String managerDn) {
    this.managerDn = managerDn;
  }

  public String getManagerPassword() {
    return managerPassword;
  }

  public void setManagerPassword(final String managerPassword) {
    this.managerPassword = managerPassword;
  }

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter(final String userSearchFilter) {
    this.userSearchFilter = userSearchFilter;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUserDnPatterns() {
    return userDnPatterns == null ? "" : userDnPatterns;
  }

  public void setUserDnPatterns(final String userDnPatterns) {
    this.userDnPatterns = userDnPatterns;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(final String domain) {
    this.domain = domain;
  }

  public String getFirstnameAttrName() {
    return firstnameAttrName;
  }

  public void setFirstnameAttrName(final String firstnameAttrName) {
    this.firstnameAttrName = firstnameAttrName;
  }

  public String getLastnameAttrName() {
    return lastnameAttrName;
  }

  public void setLastnameAttrName(final String lastnameAttrName) {
    this.lastnameAttrName = lastnameAttrName;
  }

  public String getDisplayNameAttrName() {
    return displayNameAttrName;
  }

  public void setDisplayNameAttrName(final String displayNameAttrName) {
    this.displayNameAttrName = displayNameAttrName;
  }

  public String getUserIdAttrName() {
    return userIdAttrName;
  }

  public void setUserIdAttrName(final String userIdAttrName) {
    this.userIdAttrName = userIdAttrName;
  }
}
