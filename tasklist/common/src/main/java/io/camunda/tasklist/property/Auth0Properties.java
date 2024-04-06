/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.property;

public class Auth0Properties {

  public static final String DEFAULT_ORGANIZATIONS_KEY = "https://camunda.com/orgs";

  /**
   * Defines the domain which the user always sees<br>
   * auth0.com call it <b>Custom Domain</b>
   */
  private String domain;

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

  /* Key for claim to retrieve organization info */
  private String organizationsKey = DEFAULT_ORGANIZATIONS_KEY;

  public void setOrganizationsKey(final String organizationsKey) {
    this.organizationsKey = organizationsKey;
  }

  public String getOrganizationsKey() {
    return organizationsKey;
  }

  public String getDomain() {
    return domain;
  }

  public Auth0Properties setDomain(final String domain) {
    this.domain = domain;
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
