/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

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

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
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
