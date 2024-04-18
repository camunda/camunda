package io.camunda.zeebe.spring.client.properties.common;

import io.camunda.identity.sdk.IdentityConfiguration.Type;

public class AuthProperties {

  // simple
  private String username;
  private String password;

  // oidc and saas
  private String clientId;
  private String clientSecret;

  private Type oidcType;
  private String issuer;

  public Type getOidcType() {
    return oidcType;
  }

  public void setOidcType(final Type oidcType) {
    this.oidcType = oidcType;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(final String issuer) {
    this.issuer = issuer;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
