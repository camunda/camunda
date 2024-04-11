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

import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.identity.IdentityConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtAuthenticationBuilder;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;

public class SelfManagedAuthenticationBuilder
    extends JwtAuthenticationBuilder<SelfManagedAuthenticationBuilder> {
  private IdentityConfig identityConfig;

  public SelfManagedAuthenticationBuilder withIdentityConfig(final IdentityConfig identityConfig) {
    this.identityConfig = identityConfig;
    return this;
  }

  @Override
  protected SelfManagedAuthenticationBuilder self() {
    return this;
  }

  @Override
  protected Authentication build(final JwtConfig jwtConfig) {
    return new SelfManagedAuthentication(jwtConfig, identityConfig);
  }
}
