package io.camunda.common.auth;

import io.camunda.common.auth.Authentication.AuthenticationBuilder;

public class SimpleAuthenticationBuilder implements AuthenticationBuilder {
  private String simpleUrl;
  private SimpleConfig simpleConfig;

  public SimpleAuthenticationBuilder withSimpleUrl(String simpleUrl) {
    this.simpleUrl = simpleUrl;
    return this;
  }

  public SimpleAuthenticationBuilder withSimpleConfig(SimpleConfig simpleConfig) {
    this.simpleConfig = simpleConfig;
    return this;
  }

  @Override
  public Authentication build() {
    return new SimpleAuthentication(simpleUrl, simpleConfig);
  }
}
