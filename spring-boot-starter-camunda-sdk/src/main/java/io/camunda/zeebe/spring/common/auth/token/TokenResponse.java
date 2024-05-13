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
package io.camunda.zeebe.spring.common.auth.token;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponse {

  @JsonProperty("access_token")
  private String accessToken;

  private String scope;

  @JsonProperty("expires_in")
  private Integer expiresIn;

  @JsonProperty("token_type")
  private String tokenType;

  TokenResponse() {}

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(final String accessToken) {
    this.accessToken = accessToken;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

  public Integer getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(final Integer expiresIn) {
    this.expiresIn = expiresIn;
  }

  public String getTokenType() {
    return tokenType;
  }

  public void setTokenType(final String tokenType) {
    this.tokenType = tokenType;
  }
}
