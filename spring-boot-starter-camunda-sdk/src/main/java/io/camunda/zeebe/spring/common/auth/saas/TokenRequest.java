/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.spring.common.auth.saas;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenRequest {

  private String grantType;
  private String audience;
  private String clientId;
  private String clientSecret;

  TokenRequest(final String audience, final String clientId, final String clientSecret) {
    grantType = "client_credentials";
    this.audience = audience;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @JsonProperty("grant_type")
  public String getGrantType() {
    return grantType;
  }

  public void setGrantType(final String grantType) {
    this.grantType = grantType;
  }

  @JsonProperty("audience")
  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  @JsonProperty("client_id")
  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @JsonProperty("client_secret")
  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
