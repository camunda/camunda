package io.camunda.common.auth;

import io.camunda.common.auth.identity.IdentityConfig;

public class SelfManagedAuthenticationBuilder
    extends JwtAuthenticationBuilder<SelfManagedAuthenticationBuilder> {
  private IdentityConfig identityConfig;

  public SelfManagedAuthenticationBuilder withIdentityConfig(IdentityConfig identityConfig) {
    this.identityConfig = identityConfig;
    return this;
  }

  @Override
  protected SelfManagedAuthenticationBuilder self() {
    return this;
  }

  @Override
  protected Authentication build(JwtConfig jwtConfig) {
    return new SelfManagedAuthentication(jwtConfig, identityConfig);
  }
}
