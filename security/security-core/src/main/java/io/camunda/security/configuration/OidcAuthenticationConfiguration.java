/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.AssertionConfiguration.KidDigestAlgorithm;
import io.camunda.security.configuration.AssertionConfiguration.KidEncoding;
import io.camunda.security.configuration.AssertionConfiguration.KidSource;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
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
  public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);

  private String issuerUri;
  private String clientName;
  private String clientId;
  private String clientSecret;
  private String idTokenAlgorithm = "RS256";
  private String grantType = "authorization_code";
  private String redirectUri;
  private List<String> scope = Arrays.asList("openid", "profile");
  private String jwkSetUri;
  private String authorizationUri;
  private String endSessionEndpointUri;
  private String tokenUri;
  private AuthorizeRequestConfiguration authorizeRequestConfiguration =
      new AuthorizeRequestConfiguration();
  private Set<String> audiences;
  private String usernameClaim = "sub";
  private String clientIdClaim;
  private String groupsClaim;
  private boolean preferUsernameClaim;
  private String organizationId;
  private List<String> resource;
  private String clientAuthenticationMethod = CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC;
  private AssertionConfiguration assertionConfiguration = new AssertionConfiguration();
  private Duration clockSkew = DEFAULT_CLOCK_SKEW;
  private boolean idpLogoutEnabled = true;

  @PostConstruct
  public void validate() {
    assertionConfiguration.validate();
  }

  public List<String> getResource() {
    return resource;
  }

  public void setResource(final List<String> resource) {
    this.resource = resource;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(final String issuerUri) {
    this.issuerUri = issuerUri;
  }

  public String getIdTokenAlgorithm() {
    return idTokenAlgorithm;
  }

  public void setIdTokenAlgorithm(final String idTokenAlgorithm) {
    this.idTokenAlgorithm = idTokenAlgorithm;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(final String clientName) {
    this.clientName = clientName;
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

  public String getEndSessionEndpointUri() {
    return endSessionEndpointUri;
  }

  public void setEndSessionEndpointUri(final String endSessionEndpointUri) {
    this.endSessionEndpointUri = endSessionEndpointUri;
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

  public boolean isGroupsClaimConfigured() {
    return groupsClaim != null && !groupsClaim.isBlank();
  }

  public boolean isPreferUsernameClaim() {
    return preferUsernameClaim;
  }

  public void setPreferUsernameClaim(final boolean preferUsernameClaim) {
    this.preferUsernameClaim = preferUsernameClaim;
  }

  public String getClientAuthenticationMethod() {
    return clientAuthenticationMethod;
  }

  public void setClientAuthenticationMethod(final String clientAuthenticationMethod) {
    this.clientAuthenticationMethod = clientAuthenticationMethod;
  }

  public AssertionConfiguration getAssertion() {
    return assertionConfiguration;
  }

  public void setAssertion(final AssertionConfiguration assertionConfiguration) {
    this.assertionConfiguration = assertionConfiguration;
  }

  public Duration getClockSkew() {
    return clockSkew;
  }

  public void setClockSkew(final Duration clockSkew) {
    this.clockSkew = clockSkew;
  }

  public boolean isIdpLogoutEnabled() {
    return idpLogoutEnabled;
  }

  public void setIdpLogoutEnabled(final boolean idpLogoutEnabled) {
    this.idpLogoutEnabled = idpLogoutEnabled;
  }

  public boolean isSet() {
    return issuerUri != null
        || clientId != null
        || clientName != null
        || clientSecret != null
        || !"RS256".equals(idTokenAlgorithm)
        || !"authorization_code".equals(grantType)
        || redirectUri != null
        || !Arrays.asList("openid", "profile").equals(scope)
        || jwkSetUri != null
        || authorizationUri != null
        || endSessionEndpointUri != null
        || tokenUri != null
        || authorizeRequestConfiguration == null
        || authorizeRequestConfiguration.isSet()
        || !"sub".equals(usernameClaim)
        || audiences != null
        || clientIdClaim != null
        || groupsClaim != null
        || preferUsernameClaim
        || organizationId != null
        || !CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC.equals(clientAuthenticationMethod)
        || assertionConfiguration.getKeystore().getPath() != null
        || assertionConfiguration.getKeystore().getPassword() != null
        || assertionConfiguration.getKeystore().getKeyAlias() != null
        || assertionConfiguration.getKeystore().getKeyPassword() != null
        || assertionConfiguration.getKidSource() != KidSource.PUBLIC_KEY
        || assertionConfiguration.getKidDigestAlgorithm() != KidDigestAlgorithm.SHA256
        || assertionConfiguration.getKidEncoding() != KidEncoding.BASE64URL
        || assertionConfiguration.getKidCase() != null
        || !DEFAULT_CLOCK_SKEW.equals(clockSkew)
        || !idpLogoutEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String issuerUri;
    private String clientId;
    private String clientName;
    private String clientSecret;
    private String idTokenAlgorithm = "RS256";
    private String grantType = "authorization_code";
    private String redirectUri;
    private List<String> scope = Arrays.asList("openid", "profile");
    private String jwkSetUri;
    private String authorizationUri;
    private String endSessionEndpointUri;
    private String tokenUri;
    private AuthorizeRequestConfiguration authorizeRequestConfiguration =
        new AuthorizeRequestConfiguration();
    private Set<String> audiences;
    private String usernameClaim = "sub";
    private String clientIdClaim;
    private String groupsClaim;
    private boolean preferUsernameClaim;
    private String organizationId;
    private String clientAuthenticationMethod = CLIENT_AUTHENTICATION_METHOD_CLIENT_SECRET_BASIC;
    private AssertionConfiguration assertionConfiguration = new AssertionConfiguration();
    private Duration clockSkew = DEFAULT_CLOCK_SKEW;
    private boolean idpLogoutEnabled = true;

    public Builder issuerUri(final String issuerUri) {
      this.issuerUri = issuerUri;
      return this;
    }

    public Builder clientId(final String clientId) {
      this.clientId = clientId;
      return this;
    }

    public Builder clientName(final String clientName) {
      this.clientName = clientName;
      return this;
    }

    public Builder clientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    public Builder idTokenAlgorithm(final String idTokenAlgorithm) {
      this.idTokenAlgorithm = idTokenAlgorithm;
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

    public Builder endSessionEndpointUri(final String endSessionEndpointUri) {
      this.endSessionEndpointUri = endSessionEndpointUri;
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

    public Builder preferUsernameClaim(final boolean preferUsernameClaim) {
      this.preferUsernameClaim = preferUsernameClaim;
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

    public Builder assertionConfiguration(final AssertionConfiguration assertionConfiguration) {
      this.assertionConfiguration = assertionConfiguration;
      return this;
    }

    public Builder clockSkew(final Duration clockSkew) {
      this.clockSkew = clockSkew;
      return this;
    }

    public Builder idpLogoutEnabled(final boolean idpLogoutEnabled) {
      this.idpLogoutEnabled = idpLogoutEnabled;
      return this;
    }

    public OidcAuthenticationConfiguration build() {
      final OidcAuthenticationConfiguration config = new OidcAuthenticationConfiguration();
      config.setIssuerUri(issuerUri);
      config.setClientId(clientId);
      config.setClientName(clientName);
      config.setClientSecret(clientSecret);
      config.setIdTokenAlgorithm(idTokenAlgorithm);
      config.setGrantType(grantType);
      config.setRedirectUri(redirectUri);
      config.setEndSessionEndpointUri(endSessionEndpointUri);
      config.setScope(scope);
      config.setJwkSetUri(jwkSetUri);
      config.setAuthorizationUri(authorizationUri);
      config.setTokenUri(tokenUri);
      config.setAuthorizeRequest(authorizeRequestConfiguration);
      config.setAudiences(audiences);
      config.setUsernameClaim(usernameClaim);
      config.setClientIdClaim(clientIdClaim);
      config.setGroupsClaim(groupsClaim);
      config.setPreferUsernameClaim(preferUsernameClaim);
      config.setOrganizationId(organizationId);
      config.setClientAuthenticationMethod(clientAuthenticationMethod);
      config.setAssertion(assertionConfiguration);
      config.setClockSkew(clockSkew);
      config.setIdpLogoutEnabled(idpLogoutEnabled);
      return config;
    }
  }
}
