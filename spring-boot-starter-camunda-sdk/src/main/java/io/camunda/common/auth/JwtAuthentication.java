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
package io.camunda.common.auth;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class JwtAuthentication implements Authentication {

  private final JwtConfig jwtConfig;
  private final Map<Product, JwtToken> tokens = new HashMap<>();

  protected JwtAuthentication(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
  }

  @Override
  public final Entry<String, String> getTokenHeader(final Product product) {
    if (!tokens.containsKey(product) || !isValid(tokens.get(product))) {
      final JwtToken newToken = generateToken(product, jwtConfig.getProduct(product));
      tokens.put(product, newToken);
    }
    return authHeader(tokens.get(product).getToken());
  }

  @Override
  public final void resetToken(final Product product) {
    tokens.remove(product);
  }

  protected abstract JwtToken generateToken(Product product, JwtCredential credential);

  private Entry<String, String> authHeader(final String token) {
    return new AbstractMap.SimpleEntry<>("Authorization", "Bearer " + token);
  }

  private boolean isValid(final JwtToken jwtToken) {
    return jwtToken.getExpiry().isAfter(LocalDateTime.now());
  }

  protected static class JwtToken {
    private final String token;
    private final LocalDateTime expiry;

    public JwtToken(final String token, final LocalDateTime expiry) {
      this.token = token;
      this.expiry = expiry;
    }

    public String getToken() {
      return token;
    }

    public LocalDateTime getExpiry() {
      return expiry;
    }
  }
}
