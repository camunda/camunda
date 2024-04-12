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
package io.camunda.zeebe.spring.common.auth.selfmanaged;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.identity.IdentityConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtAuthentication;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtCredential;
import java.time.LocalDateTime;

public class SelfManagedAuthentication extends JwtAuthentication {

  private final IdentityConfig identityConfig;

  public SelfManagedAuthentication(final JwtConfig jwtConfig, final IdentityConfig identityConfig) {
    super(jwtConfig);
    this.identityConfig = identityConfig;
  }

  public static SelfManagedAuthenticationBuilder builder() {
    return new SelfManagedAuthenticationBuilder();
  }

  @Override
  protected JwtToken generateToken(final Product product, final JwtCredential credential) {
    final Tokens token = getIdentityToken(product, credential);
    return new JwtToken(
        token.getAccessToken(), LocalDateTime.now().plusSeconds(token.getExpiresIn()));
  }

  private Tokens getIdentityToken(final Product product, final JwtCredential credential) {
    final Identity identity = identityConfig.get(product).getIdentity();
    final String audience = credential.getAudience();
    return identity.authentication().requestToken(audience);
  }
}
