/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for the Camunda Auth SDK under {@code camunda.auth}.
 *
 * <p>OIDC provider configuration uses Spring Security's native {@code
 * spring.security.oauth2.client.registration.*} and {@code
 * spring.security.oauth2.client.provider.*} properties. Only Camunda-specific settings are
 * configured here.
 */
@ConfigurationProperties(prefix = "camunda.auth")
public class CamundaAuthProperties {

  /** Authentication method: "oidc" or "basic". Defaults to "oidc". */
  private String method = "oidc";

  @NestedConfigurationProperty private final BasicAuthProperties basic = new BasicAuthProperties();

  @NestedConfigurationProperty private final OidcProperties oidc = new OidcProperties();

  @NestedConfigurationProperty
  private final TokenExchangeProperties tokenExchange = new TokenExchangeProperties();

  @NestedConfigurationProperty private final OboProperties obo = new OboProperties();

  @NestedConfigurationProperty private final SessionProperties session = new SessionProperties();

  @NestedConfigurationProperty
  private final PersistenceProperties persistence = new PersistenceProperties();

  @NestedConfigurationProperty private final SecurityProperties security = new SecurityProperties();

  private boolean unprotectedApi = false;

  public String getMethod() {
    return method;
  }

  public void setMethod(final String method) {
    this.method = method;
  }

  public BasicAuthProperties getBasic() {
    return basic;
  }

  public OidcProperties getOidc() {
    return oidc;
  }

  public TokenExchangeProperties getTokenExchange() {
    return tokenExchange;
  }

  public OboProperties getObo() {
    return obo;
  }

  public SessionProperties getSession() {
    return session;
  }

  public PersistenceProperties getPersistence() {
    return persistence;
  }

  public SecurityProperties getSecurity() {
    return security;
  }

  public boolean isUnprotectedApi() {
    return unprotectedApi;
  }

  public void setUnprotectedApi(final boolean unprotectedApi) {
    this.unprotectedApi = unprotectedApi;
  }

  public static class BasicAuthProperties {
    /** Whether secondary storage (database) is available. Required for basic auth. */
    private boolean secondaryStorageAvailable = false;

    public boolean isSecondaryStorageAvailable() {
      return secondaryStorageAvailable;
    }

    public void setSecondaryStorageAvailable(final boolean secondaryStorageAvailable) {
      this.secondaryStorageAvailable = secondaryStorageAvailable;
    }
  }

  /** Camunda-specific OIDC claim mapping properties. */
  public static class OidcProperties {
    private String usernameClaim = "preferred_username";
    private String clientIdClaim = "azp";
    private String groupsClaim = "";
    private boolean preferUsernameClaim = true;

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

    public String getGroupsClaim() {
      return groupsClaim;
    }

    public void setGroupsClaim(final String groupsClaim) {
      this.groupsClaim = groupsClaim;
    }

    public boolean isPreferUsernameClaim() {
      return preferUsernameClaim;
    }

    public void setPreferUsernameClaim(final boolean preferUsernameClaim) {
      this.preferUsernameClaim = preferUsernameClaim;
    }
  }

  public static class TokenExchangeProperties {
    private boolean enabled = false;

    /**
     * The Spring Security OAuth2 Client registration ID to use for token exchange. This maps to a
     * {@code spring.security.oauth2.client.registration.<id>} entry configured with {@code
     * authorization-grant-type=urn:ietf:params:oauth:grant-type:token-exchange}.
     */
    private String clientRegistrationId = "token-exchange";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getClientRegistrationId() {
      return clientRegistrationId;
    }

    public void setClientRegistrationId(final String clientRegistrationId) {
      this.clientRegistrationId = clientRegistrationId;
    }
  }

  public static class OboProperties {
    private boolean enabled = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class PersistenceProperties {
    /**
     * Storage backend: "elasticsearch" or "rdbms". Determines which persistence adapters are
     * activated.
     */
    private String type;

    /**
     * Persistence mode: "standalone" or "external".
     *
     * <p>"standalone" — the library owns all reads AND writes. The persistence store is the source
     * of truth. Suitable for deployments without an external system populating the storage (e.g.,
     * Camunda Hub).
     *
     * <p>"external" — an external system populates the storage (e.g., Zeebe exporters). The library
     * only reads. Write port beans are not created.
     */
    private String mode = "standalone";

    @NestedConfigurationProperty private final RdbmsProperties rdbms = new RdbmsProperties();

    @NestedConfigurationProperty
    private final ElasticsearchPersistenceProperties elasticsearch =
        new ElasticsearchPersistenceProperties();

    public String getType() {
      return type;
    }

    public void setType(final String type) {
      this.type = type;
    }

    public String getMode() {
      return mode;
    }

    public void setMode(final String mode) {
      this.mode = mode;
    }

    public RdbmsProperties getRdbms() {
      return rdbms;
    }

    public ElasticsearchPersistenceProperties getElasticsearch() {
      return elasticsearch;
    }
  }

  public static class RdbmsProperties {
    private boolean enabled = true;
    private boolean autoMigrate = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isAutoMigrate() {
      return autoMigrate;
    }

    public void setAutoMigrate(final boolean autoMigrate) {
      this.autoMigrate = autoMigrate;
    }
  }

  public static class SessionProperties {
    /** Whether to enable HTTP session-based authentication holding. */
    private boolean enabled = false;

    /** How often to refresh authentication from session. ISO-8601 duration. */
    private String refreshInterval = "PT30S";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getRefreshInterval() {
      return refreshInterval;
    }

    public void setRefreshInterval(final String refreshInterval) {
      this.refreshInterval = refreshInterval;
    }
  }

  public static class ElasticsearchPersistenceProperties {
    private boolean enabled = false;
    private String indexPrefix = "camunda-auth";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getIndexPrefix() {
      return indexPrefix;
    }

    public void setIndexPrefix(final String indexPrefix) {
      this.indexPrefix = indexPrefix;
    }
  }

  public static class SecurityProperties {
    private Set<String> unprotectedPaths =
        Set.of(
            "/error",
            "/actuator/**",
            "/ready",
            "/health",
            "/startup",
            "/post-logout",
            "/swagger/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/favicon.ico");

    private Set<String> apiPaths = Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**");

    private Set<String> unprotectedApiPaths =
        Set.of("/v2/license", "/v2/setup/user", "/v2/status", "/v1/external/process/**");

    private Set<String> webappPaths =
        Set.of("/login/**", "/logout", "/", "/sso-callback/**", "/oauth2/authorization/**");

    private boolean webappEnabled = false;

    private boolean csrfEnabled = true;

    private String csrfTokenName = "X-CSRF-TOKEN";

    private String sessionCookie = "camunda-session";

    private boolean idpLogoutEnabled = true;

    public Set<String> getUnprotectedPaths() {
      return unprotectedPaths;
    }

    public void setUnprotectedPaths(final Set<String> unprotectedPaths) {
      this.unprotectedPaths = unprotectedPaths;
    }

    public Set<String> getApiPaths() {
      return apiPaths;
    }

    public void setApiPaths(final Set<String> apiPaths) {
      this.apiPaths = apiPaths;
    }

    public Set<String> getUnprotectedApiPaths() {
      return unprotectedApiPaths;
    }

    public void setUnprotectedApiPaths(final Set<String> unprotectedApiPaths) {
      this.unprotectedApiPaths = unprotectedApiPaths;
    }

    public Set<String> getWebappPaths() {
      return webappPaths;
    }

    public void setWebappPaths(final Set<String> webappPaths) {
      this.webappPaths = webappPaths;
    }

    public boolean isWebappEnabled() {
      return webappEnabled;
    }

    public void setWebappEnabled(final boolean webappEnabled) {
      this.webappEnabled = webappEnabled;
    }

    public boolean isCsrfEnabled() {
      return csrfEnabled;
    }

    public void setCsrfEnabled(final boolean csrfEnabled) {
      this.csrfEnabled = csrfEnabled;
    }

    public String getCsrfTokenName() {
      return csrfTokenName;
    }

    public void setCsrfTokenName(final String csrfTokenName) {
      this.csrfTokenName = csrfTokenName;
    }

    public String getSessionCookie() {
      return sessionCookie;
    }

    public void setSessionCookie(final String sessionCookie) {
      this.sessionCookie = sessionCookie;
    }

    public boolean isIdpLogoutEnabled() {
      return idpLogoutEnabled;
    }

    public void setIdpLogoutEnabled(final boolean idpLogoutEnabled) {
      this.idpLogoutEnabled = idpLogoutEnabled;
    }
  }
}
