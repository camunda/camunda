package io.camunda.common.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenRequest {

  private String grantType;
  private String audience;
  private String clientId;
  private String clientSecret;

  @JsonProperty("grant_type")
  public String getGrantType() {
    return grantType;
  }

  public void setGrantType(String grantType) {
    this.grantType = grantType;
  }

  @JsonProperty("audience")
  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  @JsonProperty("client_id")
  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  @JsonProperty("client_secret")
  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  // TODO: Use builder pattern

  TokenRequest(String audience, String clientId, String clientSecret) {
    this.grantType = "client_credentials";
    this.audience = audience;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }
}
