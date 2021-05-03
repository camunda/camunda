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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class ZeebeClientCredentials {

  @JsonAlias({"accesstoken", "access_token"})
  private String accessToken;

  private ZonedDateTime expiry;

  @JsonAlias({"tokentype", "token_type"})
  private String tokenType;

  public ZeebeClientCredentials() {}

  public ZeebeClientCredentials(
      final String accessToken, final ZonedDateTime expiry, final String tokenType) {
    this.accessToken = accessToken;
    this.expiry = expiry;
    this.tokenType = tokenType;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  @JsonSetter("expiry")
  public void setExpiry(final String expiry) {
    this.expiry = ZonedDateTime.parse(expiry);
  }

  @JsonSetter("expires_in")
  public void setExpiresIn(final String expiresIn) {
    expiry = ZonedDateTime.now().plusSeconds(Long.parseLong(expiresIn));
  }

  @JsonGetter("expiry")
  public String getExpiry() {
    return expiry.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  @JsonIgnore
  public boolean isValid() {
    return expiry.toInstant().isAfter(Instant.now());
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, expiry, tokenType);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || !o.getClass().equals(getClass())) {
      return false;
    }

    final ZeebeClientCredentials other = (ZeebeClientCredentials) o;

    return accessToken.equals(other.accessToken) && tokenType.equals(other.tokenType);
  }
}
