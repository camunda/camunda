/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.auth.OidcGroupsLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class OidcAuthenticationConfiguration {
  public static final String GROUPS_CLAIM_PROPERTY =
      "camunda.security.authentication.oidc.groupsClaim";

  public static final String CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC =
      "client_secret_basic";
  public static final String CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT = "private_key_jwt";
  public static final List<String> CLIENT_AUTHENTICATION_METHODS =
      List.of(
          CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC,
          CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT);

  private String issuerUri;
  private String clientId;
  private String clientSecret;
  private String grantType = "authorization_code";
  private String redirectUri;
  private List<String> scope = Arrays.asList("openid", "profile");
  private String jwkSetUri;
  private String authorizationUri;
  private String tokenUri;
  private AuthorizeRequestConfiguration authorizeRequestConfiguration =
      new AuthorizeRequestConfiguration();
  private Set<String> audiences;
  private String usernameClaim = "sub";
  private String clientIdClaim;
  private String groupsClaim;
  private String organizationId;
  private List<String> resource;

  public List<String> getResource() {
    return resource;
  }

  public void setResource(final List<String> resource) {
    this.resource = resource;
  }
  private String clientAuthenticationMethod = CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC;
  private AssertionKeystoreConfiguration assertionKeystoreConfiguration =
      new AssertionKeystoreConfiguration();

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(final String issuerUri) {
    this.issuerUri = issuerUri;
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

  public String getGrantType() {
    return grantType;
  }

  public void setGrantType(final String grantType) {
    this.grantType = grantType;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(final String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public List<String> getScope() {
    return scope;
  }

  public void setScope(final List<String> scope) {
    this.scope = scope;
  }

  public String getJwkSetUri() {
    return jwkSetUri;
  }

  public void setJwkSetUri(final String jwkSetUri) {
    this.jwkSetUri = jwkSetUri;
  }

  public String getAuthorizationUri() {
    return authorizationUri;
  }

  public void setAuthorizationUri(final String authorizationUri) {
    this.authorizationUri = authorizationUri;
  }

  public String getTokenUri() {
    return tokenUri;
  }

  public void setTokenUri(final String tokenUri) {
    this.tokenUri = tokenUri;
  }

  public AuthorizeRequestConfiguration getAuthorizeRequest() {
    return authorizeRequestConfiguration;
  }

  public void setAuthorizeRequest(
      final AuthorizeRequestConfiguration authorizeRequestConfiguration) {
    this.authorizeRequestConfiguration = authorizeRequestConfiguration;
  }

  public Set<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(final Set<String> audiences) {
    this.audiences = audiences;
  }

  public String getUsernameClaim() {
    return usernameClaim;
  }

  public void setUsernameClaim(final String usernameClaim) {
    this.usernameClaim = usernameClaim;
  }

  public String getClientIdClaim() {
    return clientIdClaim;
  }

  public void setClientIdClaim(final String clientIdClaim) {
    this.clientIdClaim = clientIdClaim;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getGroupsClaim() {
    return groupsClaim;
  }

  public void setGroupsClaim(final String groupsClaim) {
    new OidcGroupsLoader(groupsClaim);
    this.groupsClaim = groupsClaim;
  }

  public String getClientAuthenticationMethod() {
    return clientAuthenticationMethod;
  }

  public void setClientAuthenticationMethod(final String clientAuthenticationMethod) {
    this.clientAuthenticationMethod = clientAuthenticationMethod;
  }

  public boolean isClientAuthenticationPrivateKeyJwt() {
    return clientAuthenticationMethod != null
        && clientAuthenticationMethod.equals(CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT);
  }

  public AssertionKeystoreConfiguration getAssertionKeystore() {
    return assertionKeystoreConfiguration;
  }

  public void setAssertionKeystore(
      final AssertionKeystoreConfiguration assertionKeystoreConfiguration) {
    this.assertionKeystoreConfiguration = assertionKeystoreConfiguration;
  }

  public boolean isSet() {
    return issuerUri != null
        || clientId != null
        || clientSecret != null
        || !"authorization_code".equals(grantType)
        || redirectUri != null
        || !Arrays.asList("openid", "profile").equals(scope)
        || jwkSetUri != null
        || authorizationUri != null
        || tokenUri != null
        || authorizeRequestConfiguration == null
        || authorizeRequestConfiguration.isSet()
        || !"sub".equals(usernameClaim)
        || audiences != null
        || clientIdClaim != null
        || groupsClaim != null
        || organizationId != null
        || !CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC.equals(clientAuthenticationMethod)
        || assertionKeystoreConfiguration.getPath() != null
        || assertionKeystoreConfiguration.getPassword() != null
        || assertionKeystoreConfiguration.getKeyAlias() != null
        || assertionKeystoreConfiguration.getKeyPassword() != null;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String issuerUri;
    private String clientId;
    private String clientSecret;
    private String grantType = "authorization_code";
    private String redirectUri;
    private List<String> scope = Arrays.asList("openid", "profile");
    private String jwkSetUri;
    private String authorizationUri;
    private String tokenUri;
    private AuthorizeRequestConfiguration authorizeRequestConfiguration =
        new AuthorizeRequestConfiguration();
    private Set<String> audiences;
    private String usernameClaim = "sub";
    private String clientIdClaim;
    private String groupsClaim;
    private String organizationId;
    private String clientAuthenticationMethod = CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC;
    private AssertionKeystoreConfiguration assertionKeystoreConfiguration =
        new AssertionKeystoreConfiguration();

    public Builder issuerUri(final String issuerUri) {
      this.issuerUri = issuerUri;
      return this;
    }

    public Builder clientId(final String clientId) {
      this.clientId = clientId;
      return this;
    }

    public Builder clientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    public Builder grantType(final String grantType) {
      this.grantType = grantType;
      return this;
    }

    public Builder redirectUri(final String redirectUri) {
      this.redirectUri = redirectUri;
      return this;
    }

    public Builder scope(final List<String> scope) {
      this.scope = scope;
      return this;
    }

    public Builder jwkSetUri(final String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
      return this;
    }

    public Builder authorizationUri(final String authorizationUri) {
      this.authorizationUri = authorizationUri;
      return this;
    }

    public Builder tokenUri(final String tokenUri) {
      this.tokenUri = tokenUri;
      return this;
    }

    public Builder authorizeRequestConfiguration(
        final AuthorizeRequestConfiguration authorizeRequestConfiguration) {
      this.authorizeRequestConfiguration = authorizeRequestConfiguration;
      return this;
    }

    public Builder audiences(final Set<String> audiences) {
      this.audiences = audiences;
      return this;
    }

    public Builder usernameClaim(final String usernameClaim) {
      this.usernameClaim = usernameClaim;
      return this;
    }

    public Builder clientIdClaim(final String clientIdClaim) {
      this.clientIdClaim = clientIdClaim;
      return this;
    }

    public Builder groupsClaim(final String groupsClaim) {
      new OidcGroupsLoader(groupsClaim); // Validation from original setter
      this.groupsClaim = groupsClaim;
      return this;
    }

    public Builder organizationId(final String organizationId) {
      this.organizationId = organizationId;
      return this;
    }

    public Builder clientAuthenticationMethod(final String clientAuthenticationMethod) {
      this.clientAuthenticationMethod = clientAuthenticationMethod;
      return this;
    }

    public Builder assertionKeystoreConfiguration(
        final AssertionKeystoreConfiguration keystoreConfiguration) {
      assertionKeystoreConfiguration = keystoreConfiguration;
      return this;
    }

    public OidcAuthenticationConfiguration build() {
      final OidcAuthenticationConfiguration config = new OidcAuthenticationConfiguration();
      config.setIssuerUri(issuerUri);
      config.setClientId(clientId);
      config.setClientSecret(clientSecret);
      config.setGrantType(grantType);
      config.setRedirectUri(redirectUri);
      config.setScope(scope);
      config.setJwkSetUri(jwkSetUri);
      config.setAuthorizationUri(authorizationUri);
      config.setTokenUri(tokenUri);
      config.setAuthorizeRequest(authorizeRequestConfiguration);
      config.setAudiences(audiences);
      config.setUsernameClaim(usernameClaim);
      config.setClientIdClaim(clientIdClaim);
      config.setGroupsClaim(groupsClaim);
      config.setOrganizationId(organizationId);
      config.setClientAuthenticationMethod(clientAuthenticationMethod);
      config.setAssertionKeystore(assertionKeystoreConfiguration);
      return config;
    }
  }
}
