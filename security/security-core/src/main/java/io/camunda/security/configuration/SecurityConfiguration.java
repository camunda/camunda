/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.regex.Pattern;

/** Will be populated with the configuration properties of 'camunda.security' */
public class SecurityConfiguration {

  /** 1 or more alphanumeric characters, '_', '@', '.', '+', '-' or '~'. */
  public static final String DEFAULT_ID_REGEX = "^[a-zA-Z0-9_~@.+-]+$";

  public static final Pattern DEFAULT_EXTERNAL_ID_REGEX = Pattern.compile(".*", Pattern.DOTALL);

  private AuthorizationsConfiguration authorizations = new AuthorizationsConfiguration();
  private InitializationConfiguration initialization = new InitializationConfiguration();
  private MultiTenancyConfiguration multiTenancy = new MultiTenancyConfiguration();
  private SaasConfiguration saas = new SaasConfiguration();

  /**
   * The ID validation pattern is configurable with the intention to:
   *
   * <ul>
   *   <li>allow customers to use even more strict validation
   *   <li>be able to react quickly if there was any ReDoS vulnerability within the default pattern
   * </ul>
   */
  private String idValidationPattern = DEFAULT_ID_REGEX;

  private Pattern compiledIdValidationPattern;

  public AuthorizationsConfiguration getAuthorizations() {
    return authorizations;
  }

  public void setAuthorizations(final AuthorizationsConfiguration authorizations) {
    this.authorizations = authorizations;
  }

  public InitializationConfiguration getInitialization() {
    return initialization;
  }

  public void setInitialization(final InitializationConfiguration initialization) {
    this.initialization = initialization;
  }

  public MultiTenancyConfiguration getMultiTenancy() {
    return multiTenancy;
  }

  public void setMultiTenancy(final MultiTenancyConfiguration multiTenancy) {
    this.multiTenancy = multiTenancy;
  }

  public SaasConfiguration getSaas() {
    return saas;
  }

  public void setSaas(final SaasConfiguration saas) {
    this.saas = saas;
  }

  public String getIdValidationPattern() {
    return idValidationPattern;
  }

  public void setIdValidationPattern(final String idValidationPattern) {
    this.idValidationPattern = idValidationPattern;
  }

  public Pattern getCompiledIdValidationPattern() {
    if (compiledIdValidationPattern == null) {
      compiledIdValidationPattern = Pattern.compile(idValidationPattern);
    }
    return compiledIdValidationPattern;
  }

  /**
   * Returns the compiled group ID validation pattern. If groups are sourced from an external IdP
   * (indicated by a non-empty groupsClaim), a permissive pattern is returned since the IdP controls
   * group naming. Otherwise, the standard ID validation pattern is used.
   *
   * @param groupsClaim the OIDC groups claim name, or null/empty if groups are not from an IdP
   */
  public Pattern getCompiledGroupIdValidationPattern(final String groupsClaim) {
    if (groupsClaim != null && !groupsClaim.isEmpty()) {
      return DEFAULT_EXTERNAL_ID_REGEX;
    }
    return getCompiledIdValidationPattern();
  }
}
