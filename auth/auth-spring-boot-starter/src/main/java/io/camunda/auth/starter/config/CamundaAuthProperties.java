/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter.config;

import java.util.Map;
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

  @NestedConfigurationProperty private final OidcProperties oidc = new OidcProperties();

  @NestedConfigurationProperty
  private final TokenExchangeProperties tokenExchange = new TokenExchangeProperties();

  @NestedConfigurationProperty private final OboProperties obo = new OboProperties();

  @NestedConfigurationProperty
  private final PersistenceProperties persistence = new PersistenceProperties();

  public String getMethod() {
    return method;
  }

  public void setMethod(final String method) {
    this.method = method;
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

  public PersistenceProperties getPersistence() {
    return persistence;
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
    private int maxDelegationChainDepth = 2;
    private Map<String, String> targetAudiences = Map.of();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxDelegationChainDepth() {
      return maxDelegationChainDepth;
    }

    public void setMaxDelegationChainDepth(final int maxDelegationChainDepth) {
      this.maxDelegationChainDepth = maxDelegationChainDepth;
    }

    public Map<String, String> getTargetAudiences() {
      return targetAudiences;
    }

    public void setTargetAudiences(final Map<String, String> targetAudiences) {
      this.targetAudiences = targetAudiences;
    }
  }

  public static class PersistenceProperties {
    @NestedConfigurationProperty private final RdbmsProperties rdbms = new RdbmsProperties();

    @NestedConfigurationProperty
    private final ElasticsearchPersistenceProperties elasticsearch =
        new ElasticsearchPersistenceProperties();

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
}
