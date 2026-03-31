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
package io.camunda.client.impl;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CamundaClientCredentials {

  /**
   * Grace period before actual token expiry during which the token is considered invalid,
   * triggering a proactive refresh. This prevents a race where a token that is valid at check time
   * expires before the request reaches the server.
   */
  private static final Duration EXPIRY_GRACE_PERIOD = Duration.ofSeconds(30);

  /**
   * Soft expiry period before actual token expiry during which the token is still valid but a
   * background refresh should be triggered. This is larger than the grace period, giving the
   * background refresh time to complete before the token becomes invalid.
   */
  private static final Duration PROACTIVE_REFRESH_PERIOD = Duration.ofSeconds(60);

  @JsonAlias({"accesstoken", "access_token"})
  private String accessToken;

  private ZonedDateTime expiry;

  @JsonAlias({"tokentype", "token_type"})
  private String tokenType;

  public CamundaClientCredentials() {}

  public CamundaClientCredentials(
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

  @JsonSetter("expires_in")
  public void setExpiresIn(final String expiresIn) {
    expiry = ZonedDateTime.now().plusSeconds(Long.parseLong(expiresIn));
  }

  @JsonGetter("expiry")
  public String getExpiry() {
    return expiry.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  @JsonSetter("expiry")
  public void setExpiry(final String expiry) {
    this.expiry = ZonedDateTime.parse(expiry);
  }

  @JsonIgnore
  public boolean isValid() {
    return expiry.toInstant().minus(EXPIRY_GRACE_PERIOD).isAfter(Instant.now());
  }

  /**
   * Returns true if the token is still valid but nearing expiry and should be refreshed in the
   * background. This allows the current token to keep being served while a new one is fetched
   * asynchronously, avoiding the cliff edge where all threads discover the token is invalid at the
   * same time.
   */
  @JsonIgnore
  public boolean shouldRefreshProactively() {
    final Instant now = Instant.now();
    final Instant expiryInstant = expiry.toInstant();
    // Token is in the proactive refresh window: still valid but nearing expiry
    return expiryInstant.minus(PROACTIVE_REFRESH_PERIOD).isBefore(now)
        && expiryInstant.minus(EXPIRY_GRACE_PERIOD).isAfter(now);
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

    final CamundaClientCredentials other = (CamundaClientCredentials) o;

    return accessToken.equals(other.accessToken) && tokenType.equals(other.tokenType);
  }
}
