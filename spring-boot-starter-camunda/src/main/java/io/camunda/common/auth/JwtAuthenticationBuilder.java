package io.camunda.common.auth;

import io.camunda.common.auth.Authentication.AuthenticationBuilder;

public abstract class JwtAuthenticationBuilder<T extends JwtAuthenticationBuilder<?>>
    implements AuthenticationBuilder {
  private JwtConfig jwtConfig;

  public final T withJwtConfig(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    return self();
  }

  @Override
  public final Authentication build() {
    return build(jwtConfig);
  }

  protected abstract T self();

  protected abstract Authentication build(JwtConfig jwtConfig);
}
