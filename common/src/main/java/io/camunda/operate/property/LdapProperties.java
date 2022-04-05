/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

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

  public void setBaseDn(String baseDn) {
    this.baseDn = baseDn;
  }

  public String getUserSearchBase() {
    return userSearchBase == null ? "" : userSearchBase;
  }

  public void setUserSearchBase(String userSearchBase) {
    this.userSearchBase = userSearchBase;
  }

  public String getManagerDn() {
    return managerDn;
  }

  public void setManagerDn(String managerDn) {
    this.managerDn = managerDn;
  }

  public String getManagerPassword() {
    return managerPassword;
  }

  public void setManagerPassword(String managerPassword) {
    this.managerPassword = managerPassword;
  }

  public String getUserSearchFilter() { return userSearchFilter; }

  public void setUserSearchFilter(String userSearchFilter) {
    this.userSearchFilter = userSearchFilter;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUserDnPatterns() {
    return userDnPatterns == null ? "" : userDnPatterns;
  }

  public void setUserDnPatterns(String userDnPatterns) {
    this.userDnPatterns = userDnPatterns;
  }

  public String getDomain() { return domain; }

  public void setDomain(String domain) { this.domain = domain; }

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
