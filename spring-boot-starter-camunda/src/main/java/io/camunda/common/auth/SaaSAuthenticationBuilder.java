package io.camunda.common.auth;

import io.camunda.common.json.JsonMapper;

public class SaaSAuthenticationBuilder extends JwtAuthenticationBuilder<SaaSAuthenticationBuilder> {
  private JsonMapper jsonMapper;

  public SaaSAuthenticationBuilder withJsonMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    return this;
  }

  @Override
  protected SaaSAuthenticationBuilder self() {
    return this;
  }

  @Override
  protected SaaSAuthentication build(JwtConfig jwtConfig) {
    return new SaaSAuthentication(jwtConfig, jsonMapper);
  }
}
