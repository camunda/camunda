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
package io.zeebe.client.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class ZeebeClientCredentials {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private long expiresIn;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("scope")
  private String scope;

  public ZeebeClientCredentials() {}

  public ZeebeClientCredentials(
      final String accessToken, final long expiresIn, final String tokenType, final String scope) {
    this.accessToken = accessToken;
    this.expiresIn = expiresIn;
    this.tokenType = tokenType;
    this.scope = scope;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, expiresIn, tokenType, scope);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || !o.getClass().equals(this.getClass())) {
      return false;
    }

    final ZeebeClientCredentials other = (ZeebeClientCredentials) o;

    return accessToken.equals(other.accessToken)
        && expiresIn == other.expiresIn
        && tokenType.equals(other.tokenType)
        && scope.equals(other.scope);
  }
}
